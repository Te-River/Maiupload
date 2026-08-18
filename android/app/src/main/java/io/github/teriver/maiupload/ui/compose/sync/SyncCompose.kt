package io.github.teriver.maiupload.ui.compose.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.teriver.maiupload.Application.Companion.application
import io.github.teriver.maiupload.GlobalViewModel
import io.github.teriver.maiupload.core.data.GameType
import io.github.teriver.maiupload.core.data.chuni.ChuniScoreManager.writeChuniScoreCache
import io.github.teriver.maiupload.core.data.maimai.MaimaiScoreManager.writeMaimaiScoreCache
import io.github.teriver.maiupload.core.prober.ProberPlatform
import io.github.teriver.maiupload.core.prober.divingfish.DivingFishOAuthUtil
import io.github.teriver.maiupload.core.prober.lxns.LxnsOAuthUtil
import io.github.teriver.maiupload.core.prober.rival.RivalSyncUtil
import io.github.teriver.maiupload.core.prober.sendMessageToUi
import io.github.teriver.maiupload.core.proxy.HttpServer
import io.github.teriver.maiupload.ui.component.ConfirmDialog
import io.github.teriver.maiupload.ui.component.DownloadDialog
import io.github.teriver.maiupload.ui.component.InfoDialog
import io.github.teriver.maiupload.ui.component.WindowInsetsSpacer
import io.github.teriver.maiupload.ui.compose.scores.refreshScore
import io.github.teriver.maiupload.ui.compose.scores.resources
import io.github.teriver.maiupload.ui.compose.setting.PasswordTextFiled
import io.github.teriver.maiupload.vpn.core.LocalVpnService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCompose() {
    val context = LocalContext.current
    val viewModel = remember { SyncViewModel }
    val globalViewModel = remember { GlobalViewModel }

    var divingfishToken by remember { mutableStateOf(application.configManager.config.divingfishToken) }
    var lxnsToken by remember { mutableStateOf(application.configManager.config.lxnsToken) }

    var openAskIsOverwriteScoresDialog by remember { mutableStateOf(false) }
    var openAskOverwriteUserInfo by remember { mutableStateOf(false) }
    var rivalSyncing by remember { mutableStateOf(false) }

    val vpnRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService(context as Activity)
        }
    }

    when {
        openAskOverwriteUserInfo -> {
            ConfirmDialog(
                info = "是否获取个人信息并覆盖当前个人信息",
                onRequest = {
                    val token = when (globalViewModel.proberPlatform) {
                        ProberPlatform.DIVING_FISH ->
                            // 水鱼 OAuth 模式下用 access_token（prober 内部会自动刷新），
                            // Token 模式沿用 Import-Token。
                            if (SyncViewModel.divingfishTokenInputMode == 1)
                                application.configManager.config.divingfishOAuthAccessToken
                            else
                                application.configManager.config.divingfishToken
                        // 落雪 OAuth 模式下用 access_token（prober 内部会自动刷新），
                        // Token 模式沿用 personal lxnsToken。
                        ProberPlatform.LXNS ->
                            if (SyncViewModel.tokenInputMode == 1)
                                application.configManager.config.lxnsOAuthAccessToken
                            else
                                application.configManager.config.lxnsToken
                        else ->
                            application.configManager.config.lxnsToken
                    }

                    if (token.isEmpty()) {
                        sendMessageToUi("请先设置token")
                        return@ConfirmDialog
                    }

                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val proberUtil = globalViewModel.proberPlatform.factory

                        fun sendSyncSuccessMessageToUi() {
                            sendMessageToUi("成功从${
                                globalViewModel.proberPlatform.proberName
                            }同步${globalViewModel.gameType.displayName}玩家数据")
                        }

                        proberUtil.updateUserInfo(token)

                        sendSyncSuccessMessageToUi()
                    }
                }
            ) {
                openAskOverwriteUserInfo = false
            }
        }
        openAskIsOverwriteScoresDialog -> {
            ConfirmDialog(
                info = "确认同步数据并添加到数据库吗?",
                onRequest = {
                    val token = when (globalViewModel.proberPlatform) {
                        ProberPlatform.DIVING_FISH ->
                            // 水鱼 OAuth 模式下用 access_token（prober 内部会自动刷新），
                            // Token 模式沿用 Import-Token。
                            if (SyncViewModel.divingfishTokenInputMode == 1)
                                application.configManager.config.divingfishOAuthAccessToken
                            else
                                application.configManager.config.divingfishToken
                        // 落雪 OAuth 模式下用 access_token（prober 内部会自动刷新），
                        // Token 模式沿用 personal lxnsToken。
                        ProberPlatform.LXNS ->
                            if (SyncViewModel.tokenInputMode == 1)
                                application.configManager.config.lxnsOAuthAccessToken
                            else
                                application.configManager.config.lxnsToken
                        else ->
                            application.configManager.config.lxnsToken
                    }

                    if (token.isEmpty()) {
                        sendMessageToUi("请先设置token")
                        return@ConfirmDialog
                    }

                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val proberUtil = globalViewModel.proberPlatform.factory

                        fun sendSyncSuccessMessageToUi() {
                            sendMessageToUi("成功从${
                                globalViewModel.proberPlatform.proberName
                            }同步${globalViewModel.gameType.displayName}成绩")
                        }

                        when (globalViewModel.gameType) {
                            GameType.MaimaiDX -> {
                                val result = proberUtil.getMaimaiProberData(token)
                                if (result.isNotEmpty()) {
                                    writeMaimaiScoreCache(result)
                                    sendSyncSuccessMessageToUi()
                                }
                            }
                            GameType.Chunithm -> {
                                val result = proberUtil.getChuniProberData(token)
                                if (result.isNotEmpty()) {
                                    writeChuniScoreCache(result)
                                    sendSyncSuccessMessageToUi()
                                }
                            }
                        }
                    }
                }
            ) {
                openAskIsOverwriteScoresDialog = false
            }
        }
        SyncViewModel.openInitDialog -> {
            InfoDialog("首次启动需要下载资源文件，请耐心等待") {
                SyncViewModel.openInitDialog = false
                SyncViewModel.openInitDownloadDialog = true
                SyncViewModel.downloadComplateMethod = {
                    SyncViewModel.openInitDownloadDialog  = false
                }
            }
        }
        SyncViewModel.openInitDownloadDialog -> {
            DownloadDialog(
                resources
            ) {
                SyncViewModel.downloadComplateMethod()
            }
        }
    }

    @Composable
    fun partA(
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    modifier = Modifier
                        .padding(16.dp),
                    onClick = {
                        if (!checkResourceComplate(context)) {
                            SyncViewModel.openInitDialog = true
                        } else {
                            if (!globalViewModel.isVpnServiceRunning) {
                                val intent = VpnService.prepare(context)
                                if (intent != null) {
                                    vpnRequestLauncher.launch(intent)
                                } else {
                                    startVpnService(context as Activity)
                                }
                                application.startHttpServer()
                            } else {
                                stopVpnService(context as Activity)
                                application.stopHttpServer()
                            }
                        }
                    },
                    enabled = !GlobalViewModel.maimaiHooking || !GlobalViewModel.chuniHooking
                ) {
                    if (!globalViewModel.isVpnServiceRunning)
                        Text("开启劫持")
                    else Text("结束劫持")
                }

                Button(
                    modifier = Modifier
                        .padding(16.dp),
                    onClick = { application.startWechat() }
                ) {
                    Text("启动微信")
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
                    .fillMaxWidth()
            ) {
                ProberPlatform.entries.forEach {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = it.ordinal,
                            count = ProberPlatform.entries.size,
                        ),
                        onClick = { globalViewModel.proberPlatform = it },
                        selected = it == globalViewModel.proberPlatform
                    ) {
                        Text(it.proberName)
                    }
                }
            }

            // 落雪/水鱼查分器选中时，在游戏选择行原位置渐次出现 OAuth / Token 切换按钮，
            // 游戏选择行随之被推下移；切到其它查分器时该行收缩消失，游戏行复位。
            // 用 AnimatedVisibility + expandVertically/shrinkVertically 实现下移动画。
            val isLxnsSelected = globalViewModel.proberPlatform == ProberPlatform.LXNS
            val isDfSelected = globalViewModel.proberPlatform == ProberPlatform.DIVING_FISH
            AnimatedVisibility(
                visible = isLxnsSelected || isDfSelected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                ) {
                    // 水鱼/落雪各自记住自己的输入模式，互不影响
                    val tokenInputMode = if (isLxnsSelected) SyncViewModel.tokenInputMode
                    else SyncViewModel.divingfishTokenInputMode
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = 0, count = 2
                        ),
                        selected = tokenInputMode == 1,
                        onClick = {
                            if (isLxnsSelected) SyncViewModel.tokenInputMode = 1
                            else SyncViewModel.divingfishTokenInputMode = 1
                        }
                    ) {
                        Text("OAuth")
                    }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = 1, count = 2
                        ),
                        selected = tokenInputMode == 0,
                        onClick = {
                            if (isLxnsSelected) SyncViewModel.tokenInputMode = 0
                            else SyncViewModel.divingfishTokenInputMode = 0
                        }
                    ) {
                        Text("Token")
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .fillMaxWidth()
            ) {
                GameType.entries.forEach {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = it.ordinal, count = GameType.entries.size
                        ),
                        selected = globalViewModel.gameType == it,
                        onClick = {
                            globalViewModel.gameType = it
                            refreshScore(it)
                        },
                    ) {
                        Text(it.displayName)
                    }
                }
            }

            // 落雪/水鱼 + OAuth 模式：授权入口 + 已授权状态 + 取消授权；
            // 其它情况沿用原 Token 输入框。
            // 两侧都用 AnimatedVisibility + expand/shrinkVertically + fade，遵循 Material Motion，
            // 切到 OAuth 时那段渐次展开、Token 输入框渐次收起，切走时反过来，不瞬间蹦。
            val isLxnsOAuth =
                globalViewModel.proberPlatform == ProberPlatform.LXNS &&
                    SyncViewModel.tokenInputMode == 1
            val isDfOAuth =
                globalViewModel.proberPlatform == ProberPlatform.DIVING_FISH &&
                    SyncViewModel.divingfishTokenInputMode == 1
            val isOAuthMode = isLxnsOAuth || isDfOAuth
            val coroutineScope = rememberCoroutineScope()
            var oauthCode by remember { mutableStateOf("") }
            var oauthExchanging by remember { mutableStateOf(false) }
            val oauthAuthorized = remember {
                mutableStateOf(
                    if (isDfOAuth) DivingFishOAuthUtil.isAuthorized()
                    else LxnsOAuthUtil.isAuthorized()
                )
            }
            // 水鱼 OAuth 走本地回调（127.0.0.1:8284）：浏览器授权后自动跳回换 token，
            // 这里轮询 isAuthorized 把已授权状态刷回 UI（落雪是手动粘贴授权码，无需轮询）。
            LaunchedEffect(isDfOAuth, oauthAuthorized.value) {
                if (isDfOAuth && !oauthAuthorized.value) {
                    val deadline = System.currentTimeMillis() + 5 * 60 * 1000
                    while (System.currentTimeMillis() < deadline) {
                        delay(2000)
                        if (DivingFishOAuthUtil.isAuthorized()) {
                            oauthAuthorized.value = true
                            sendMessageToUi("水鱼授权成功")
                            break
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isOAuthMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 已授权 / 未授权两态也走 AnimatedVisibility，按 oauthAuthorized.value 切，不瞬间蹦。
                    AnimatedVisibility(
                        visible = oauthAuthorized.value,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isDfOAuth) "已授权水鱼（OAuth）" else "已授权落雪（OAuth）",
                                modifier = Modifier.padding(top = 15.dp)
                            )
                            Button(
                                modifier = Modifier
                                    .padding(15.dp)
                                    .size(300.dp, 50.dp),
                                onClick = {
                                    if (isDfOAuth) DivingFishOAuthUtil.clearTokens()
                                    else LxnsOAuthUtil.clearTokens()
                                    oauthAuthorized.value = false
                                    sendMessageToUi(if (isDfOAuth) "已取消水鱼授权" else "已取消落雪授权")
                                }
                            ) {
                                Text("取消授权")
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = !oauthAuthorized.value,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                modifier = Modifier
                                    .padding(15.dp)
                                    .size(300.dp, 50.dp),
                                onClick = {
                                    if (isDfOAuth) {
                                        // 水鱼 OAuth 走本地回调：先确保本地 8284 端口在监听，再开授权页
                                        application.startHttpServer()
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DivingFishOAuthUtil.getAuthorizeUrl()))
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        application.startActivity(intent)
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LxnsOAuthUtil.getAuthorizeUrl()))
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        application.startActivity(intent)
                                    }
                                }
                            ) {
                                Text(if (isDfOAuth) "前往水鱼授权" else "前往落雪授权")
                            }

                            // 落雪为无回调(oob)流程，需手动粘贴授权码；水鱼走本地回调自动换 token，无需手动填
                            if (!isDfOAuth) {
                                OutlinedTextField(
                                    value = oauthCode,
                                    onValueChange = { oauthCode = it },
                                    singleLine = true,
                                    label = { Text("授权码") },
                                    modifier = Modifier
                                        .padding(15.dp)
                                        .fillMaxWidth(),
                                    enabled = !oauthExchanging
                                )

                                Button(
                                    modifier = Modifier
                                        .padding(15.dp)
                                        .size(300.dp, 50.dp),
                                    enabled = oauthCode.isNotBlank() && !oauthExchanging,
                                    onClick = {
                                        oauthExchanging = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val ok = LxnsOAuthUtil.exchangeCodeForToken(oauthCode)
                                            oauthExchanging = false
                                            if (ok) {
                                                oauthAuthorized.value = true
                                                oauthCode = ""
                                            }
                                        }
                                    }
                                ) {
                                    Text(if (oauthExchanging) "换取中..." else "用授权码换取 Token")
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = !isOAuthMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                PasswordTextFiled(
                    modifier = Modifier
                        .padding(15.dp)
                        .fillMaxWidth()
                        .height(75.dp),
                    label = { Text("查分器Token") },
                    icon = { Icon(Icons.Filled.Lock, null) },
                    hidden = SyncViewModel.tokenHidden,
                    value = when (globalViewModel.proberPlatform) {
                        ProberPlatform.DIVING_FISH -> divingfishToken
                        ProberPlatform.LXNS -> lxnsToken
                        ProberPlatform.LOCAL -> ""
                    },
                    onTrailingIconClick = { SyncViewModel.tokenHidden = !SyncViewModel.tokenHidden },
                    onValueChange = {
                        when (globalViewModel.proberPlatform) {
                            ProberPlatform.DIVING_FISH -> {
                                divingfishToken = it
                                application.configManager.config.divingfishToken = it
                            }

                            else -> {
                                lxnsToken = it
                                application.configManager.config.lxnsToken = it
                            }
                        }

                    },
                    enable = globalViewModel.proberPlatform != ProberPlatform.LOCAL,
                    horizontalDivider = false,
                )
                // 水鱼 Import-Token 迁移提示：OAuth 将取代 Import-Token，建议切到 OAuth 授权
                if (globalViewModel.proberPlatform == ProberPlatform.DIVING_FISH &&
                    SyncViewModel.divingfishTokenInputMode == 0
                ) {
                    Text(
                        text = "水鱼 Import-Token 即将停用，建议切换到 OAuth 授权",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    @Composable
    fun partB(
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rival 同步仅支持舞萌 DX：切到中二节奏时隐藏按钮（Material Motion 过渡）
            AnimatedVisibility(
                visible = globalViewModel.gameType == GameType.MaimaiDX,
                enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
            ) {
            Button(
                modifier = Modifier
                    .padding(15.dp)
                    .size(300.dp, 50.dp),
                enabled = !rivalSyncing,
                onClick = {
                    // 通过 Rival 同步：类型一流程，拉对手成绩后上传到当前选定查分器（落雪 OAuth / 水鱼 Token）。
                    // 类型一仅对舞萌 DX 生效，中二节奏无 Rival API（按钮已按 gameType 隐藏）。
                    val cfg = application.configManager.config.rivalSyncConfig
                    if (cfg.userId.isBlank()) {
                        sendMessageToUi("请先在设置页 Rival 设置里填入 userId，或用 QR 二维码鉴权拿 userId")
                        return@Button
                    }
                    rivalSyncing = true
                    GlobalViewModel.viewModelScope.launch(Dispatchers.IO) {
                        try {
                            // 显式传 onProgress：切 Main 后直接 append pendingMessages 队列 + 触发弹窗，
                            // 绕开 LiveData postValue 合并丢弃中间值导致后续提示不显
                            RivalSyncUtil.uploadToProber { msg ->
                                GlobalViewModel.viewModelScope.launch(Dispatchers.Main) {
                                    GlobalViewModel.pendingMessages.add(msg)
                                    GlobalViewModel.showMessageDialog = true
                                }
                            }
                        } finally {
                            withContext(Dispatchers.Main) { rivalSyncing = false }
                        }
                    }
                }
            ) {
                // 进度条↔文本切换用 AnimatedContent 平滑过渡，避免高光消失瞬间卡顿
                AnimatedContent(
                    targetState = rivalSyncing,
                    transitionSpec = {
                        (fadeIn(tween(300, easing = FastOutSlowInEasing)) + scaleIn(tween(300, easing = FastOutSlowInEasing), initialScale = 0.8f)) togetherWith
                            (fadeOut(tween(300, easing = FastOutSlowInEasing)) + scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.8f))
                    },
                    label = "RivalSyncButtonContent"
                ) { syncing ->
                    if (syncing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(6.dp)
                        )
                    } else {
                        Text("通过 Rival 同步")
                    }
                }
            }
            }

            Button(
                modifier = Modifier
                    .padding(15.dp)
                    .size(300.dp, 50.dp),
                onClick = {
                    application.copyTextToClipboard("http://127.0.0.2:${HttpServer.Port}/${globalViewModel.gameType.ordinal}")
                }
            ) {
                Text("复制${globalViewModel.gameType.displayName} Hook链接(长期有效)")
            }

            Button(
                modifier = Modifier
                    .padding(15.dp)
                    .size(300.dp, 50.dp),
                onClick = {
                    if (!checkResourceComplate(context)) {
                        SyncViewModel.openInitDialog = true
                    } else {
                        openAskIsOverwriteScoresDialog = true
                    }
                },
                enabled = globalViewModel.proberPlatform != ProberPlatform.LOCAL
            ) {
                Text("从选定的查分器获取 ${globalViewModel.gameType.displayName} 成绩")
            }

            Button(
                modifier = Modifier
                    .padding(15.dp)
                    .size(300.dp, 50.dp),
                onClick = {
                    openAskOverwriteUserInfo = true
                },
                enabled = globalViewModel.proberPlatform != ProberPlatform.LOCAL
            ) {
                Text("从选定的查分器获取 ${globalViewModel.gameType.displayName} 个人信息")
            }

            AnimatedVisibility(
                GlobalViewModel.maimaiHooking
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("正在获取 舞萌DX 成绩")
                    LinearProgressIndicator()
                }
            }

            AnimatedVisibility(
                GlobalViewModel.chuniHooking
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("正在获取 中二节奏 成绩")
                    LinearProgressIndicator()
                }
            }

        }
    }

    if (application.isLandscape) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            WindowInsetsSpacer.TopPaddingSpacer()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                partA(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
                VerticalDivider(Modifier.fillMaxHeight())
                partB(Modifier.weight(1f))
            }
            WindowInsetsSpacer.BottomPaddingSpacer()
        }
    } else {
        Row(
            Modifier
                .fillMaxSize()
        ) {
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                WindowInsetsSpacer.TopPaddingSpacer()
                partA()
                HorizontalDivider()
                partB()
            }
        }
    }
}

private fun startVpnService(activity: Activity) {
    val prepareIntent = VpnService.prepare(activity)
    if (prepareIntent != null) {
        // 用户尚未授权，应通过 launcher 请求；这里直接启动会无效
        activity.startActivity(prepareIntent)
        return
    }
    val intent = Intent(activity, LocalVpnService::class.java)
    activity.startService(intent)
}

private fun stopVpnService(activity: Activity) {
    val intent = Intent(activity, LocalVpnService::class.java).apply {
        action = LocalVpnService.DISCONNECT_INTENT
    }
    activity.startService(intent)
}

private fun checkResourceComplate(context: Context): Boolean {
    // 必须两个曲目表都存在且非空（0 字节的空文件会导致 getLevelValue 返回 0 → DX Rating 全 0）
    val maimai = context.filesDir.resolve("maimai_song_list.json")
    val chuni = context.filesDir.resolve("chuni_song_list.json")
    return (maimai.exists() && maimai.length() > 0)
            && (chuni.exists() && chuni.length() > 0)
}
