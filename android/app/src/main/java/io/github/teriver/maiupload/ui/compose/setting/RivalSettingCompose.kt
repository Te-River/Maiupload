package io.github.teriver.maiupload.ui.compose.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.teriver.maiupload.Application.Companion.application
import io.github.teriver.maiupload.GlobalViewModel
import io.github.teriver.maiupload.core.config.ConfigTransfer
import io.github.teriver.maiupload.core.config.RivalSyncConfig
import io.github.teriver.maiupload.core.prober.rival.RivalSyncUtil
import io.github.teriver.maiupload.core.prober.sendMessageToUi
import io.github.teriver.maiupload.ui.component.UnlockDialog

/**
 * 「Rival 设置」二级菜单页：从设置页「成绩抓取设置」大类的跳转按钮进入。
 * 装载类型一（Rival 同步）的全部配置输入框 + QR 鉴权入口。
 * 输入框标签只留字段名，不举示例，由用户自填。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RivalSettingCompose(onBack: () -> Unit) {
    val config = application.configManager.config
    val rival = config.rivalSyncConfig
    // 导入带「隐藏Rival配置」锁的配置后：字段只读并掩码显示为 ""，同步调用不受影响
    val hideRival = config.hideRivalConfig
    var showUnlockRivalDialog by remember { mutableStateOf(false) }

    // 输入框镜像 state：OutlinedTextField 的 value 必须是 Compose 可观察的 state，
    // 直接读 rival.xxx（外部对象字段）重组时无变化被观察到，输入框不响应。
    // 用 mutableStateOf 镜像每个字段，onValueChange 里先写 state 再写 config + save。
    var keychip by remember { mutableStateOf(rival.keychip) }
    var gameServerUrl by remember { mutableStateOf(rival.gameServerUrl) }
    var apiHash by remember { mutableStateOf(rival.apiHash) }
    var authServerUrl by remember { mutableStateOf(rival.authServerUrl) }
    var userId by remember { mutableStateOf(rival.userId) }
    var cryptKey by remember { mutableStateOf(rival.cryptKey) }
    var cryptIv by remember { mutableStateOf(rival.cryptIv) }
    var cryptEncoding by remember { mutableStateOf(rival.cryptEncoding) }
    var cryptObfuscate by remember { mutableStateOf(rival.cryptObfuscate) }
    var authSalt by remember { mutableStateOf(rival.authSalt) }

    fun commit(field: String, value: String) {
        // 统一 trim 防用户输入首尾空格（如 gameServerUrl 尾随空格会导致
        // 拼接 URL 含 %20，服务器路径不匹配返回 200 空体）
        val trimmed = value.trim()
        when (field) {
            "keychip" -> rival.keychip = trimmed
            "gameServerUrl" -> rival.gameServerUrl = trimmed
            "apiHash" -> rival.apiHash = trimmed
            "authServerUrl" -> rival.authServerUrl = trimmed
            "userId" -> rival.userId = trimmed
            "cryptKey" -> rival.cryptKey = trimmed
            "cryptIv" -> rival.cryptIv = trimmed
            "cryptEncoding" -> rival.cryptEncoding = trimmed
            "cryptObfuscate" -> rival.cryptObfuscate = trimmed
            "authSalt" -> rival.authSalt = trimmed
        }
        application.configManager.save()
    }

    var qrCodeInput by remember { mutableStateOf("") }
    var qrAuthing by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Rival 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "类型一（Rival 同步）配置。全部留空由你自填，不内置。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hideRival) {
                Text(
                    "Rival 配置已隐藏（导入配置时启用）：字段不显示具体值，但同步功能不受影响。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = if (hideRival) "" else keychip,
                onValueChange = { keychip = it; commit("keychip", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("机台号", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = if (hideRival) "" else gameServerUrl,
                onValueChange = { gameServerUrl = it; commit("gameServerUrl", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("游戏服务器网址 (含尾斜杠)", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = if (hideRival) "" else apiHash,
                onValueChange = { apiHash = it; commit("apiHash", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("哈希", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = if (hideRival) "" else authServerUrl,
                onValueChange = { authServerUrl = it; commit("authServerUrl", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("Auth 鉴权节点", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = if (hideRival) "" else userId,
                onValueChange = { userId = it; commit("userId", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("userId", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = qrCodeInput,
                onValueChange = { qrCodeInput = it },
                singleLine = true,
                label = { Text("QR 二维码内容", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (qrAuthing) return@Button
                    qrAuthing = true
                    GlobalViewModel.viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val ok = RivalSyncUtil.authByQr(qrCodeInput.trim())
                            if (ok) {
                                qrCodeInput = ""
                                userId = rival.userId
                            }
                        } finally {
                            withContext(Dispatchers.Main) { qrAuthing = false }
                        }
                    }
                },
                enabled = !qrAuthing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (qrAuthing) "鉴权中…" else "用 QR 二维码鉴权拿 userId")
            }

            OutlinedTextField(
                value = if (hideRival) "" else cryptKey,
                onValueChange = { cryptKey = it; commit("cryptKey", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("加密 Key", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = if (hideRival) "" else cryptIv,
                onValueChange = { cryptIv = it; commit("cryptIv", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("加密 IV", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = if (hideRival) "" else cryptEncoding,
                onValueChange = { cryptEncoding = it; commit("cryptEncoding", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("编码版本", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = if (hideRival) "" else cryptObfuscate,
                onValueChange = { cryptObfuscate = it; commit("cryptObfuscate", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("混淆参数", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = if (hideRival) "" else authSalt,
                onValueChange = { authSalt = it; commit("authSalt", it) },
                enabled = !hideRival,
                singleLine = true,
                label = { Text("Auth Salt", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )

            // 隐藏Rival配置锁：解除入口（口令校验通过，或清除全部 Rival 字段）
            if (hideRival) {
                TextButton(
                    onClick = { showUnlockRivalDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("解除隐藏显示")
                }
            }
        }
    }

    if (showUnlockRivalDialog) {
        UnlockDialog(
            title = "解除隐藏Rival配置",
            description = "输入导出方提供的解除口令；忘记口令可通过清除相关配置解除（将清空全部 Rival 字段）。",
            hasCode = config.rivalUnlockCodeHash.isNotEmpty() || config.rivalUnlockData.isNotEmpty(),
            clearActionName = "清除并解除",
            clearActionHint = "将清空机台号/服务器网址/加密参数/userId 等全部 Rival 字段，此操作不可恢复",
            onUnlock = { input ->
                // 双通道校验：SHA-256 哈希或加密字段（以口令 hash 为准解密）任一通过即可
                val ok = ConfigTransfer.verifyUnlockCode(input, config.rivalUnlockCodeHash) ||
                    ConfigTransfer.verifyUnlockData(input, config.rivalUnlockData)
                if (ok) {
                    config.hideRivalConfig = false
                    config.rivalUnlockCodeHash = ""
                    config.rivalUnlockData = ""
                    application.configManager.save()
                    sendMessageToUi("已解除隐藏Rival配置")
                }
                ok
            },
            onClear = {
                // 清除相关配置：清空全部 Rival 字段（含 token），并解除锁
                config.rivalSyncConfig = RivalSyncConfig()
                config.hideRivalConfig = false
                config.rivalUnlockCodeHash = ""
                config.rivalUnlockData = ""
                application.configManager.save()
                // 同步刷新本页镜像 state，避免残留旧值
                keychip = ""
                gameServerUrl = ""
                apiHash = ""
                authServerUrl = ""
                userId = ""
                cryptKey = ""
                cryptIv = ""
                cryptEncoding = ""
                cryptObfuscate = ""
                authSalt = ""
            },
            onDismiss = { showUnlockRivalDialog = false }
        )
    }
}
