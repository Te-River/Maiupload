package io.github.teriver.maiupload.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.teriver.maiupload.Application.Companion.application
import io.github.teriver.maiupload.R
import io.github.teriver.maiupload.core.utils.Release
import io.github.teriver.maiupload.ui.compose.setting.TextButtonItem
import io.github.teriver.maiupload.ui.compose.sync.FileDownloadMeta
import io.github.teriver.maiupload.ui.theme.darken
import io.github.teriver.maiupload.ui.theme.getCardColor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val httpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 30000
        connectTimeoutMillis = 30000
    }
}

@Composable
fun InfoDialog(info: String, onRequest: () -> Unit) {
    Dialog(onDismissRequest = { onRequest() }) {
        Card(
            modifier = Modifier
                .sizeIn(maxWidth = 300.dp, minHeight = 200.dp)
                .wrapContentSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(getCardColor())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = info,
                    modifier = Modifier.padding(16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { onRequest() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    info: String,
    onRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(getCardColor())
        ) {
            Column(
                modifier = Modifier
                    .sizeIn(minHeight = 150.dp)
                    .wrapContentSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = info,
                    modifier = Modifier.padding(16.dp),
                )
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            onRequest()
                            onDismiss()
                        }
                    ) {
                        Text("确认")
                    }

                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DownloadDialog(
    downloadFileMetas: List<FileDownloadMeta>,
    onRequest: () -> Unit
) {
    var finish by remember { mutableFloatStateOf(0F) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(downloadFileMetas) {
        val totalFiles = downloadFileMetas.size
        var completedFiles = 0
        val downloadJobs = mutableListOf<Job>()

        for (meta in downloadFileMetas) {
            val job = scope.async(Dispatchers.IO) {
                val response = httpClient.get(meta.fileDownloadUrl)
                if (response.status.value == 200) {
                    val file = application.filesDir.resolve(meta.fileSavePath).resolve(meta.fileName)
                    file.parentFile?.mkdirs()
                    when {
                        meta.fileName.endsWith(".png", ignoreCase = true) -> {
                            file.outputStream().use {
                                it.write(response.readRawBytes())
                            }
                        }
                        else -> {
                            file.bufferedWriter().use {
                                it.write(response.bodyAsText())
                            }
                        }
                    }
                    completedFiles++
                    finish = completedFiles.toFloat() / totalFiles
                }
            }
            downloadJobs += job
        }
        downloadJobs.forEach { it.join() }

        onRequest()
    }

    BasicAlertDialog(
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = {},
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(getCardColor())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("正在下载资源...")
                Text("下载进度: ${(finish * 100).toInt()}%")

                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    progress = { finish }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DiffChooseDialog(
    onRequest: (List<Int>) -> Unit,
    defaultList: List<String>,
    currentChoiceList: List<Int>,
    onDismissRequest: () -> Unit
) {
    val currentChoiceDifficulties = remember {
        mutableStateListOf(*currentChoiceList.toTypedArray()) }

    BasicAlertDialog(
        modifier = Modifier.wrapContentSize(),
        onDismissRequest = onDismissRequest,
    ) {
        Card(
            modifier = Modifier.padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = getCardColor())
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("选择要爬取的难度")

                defaultList.forEachIndexed { index, item ->
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item)
                        Checkbox(
                            checked = index in currentChoiceDifficulties,
                            onCheckedChange = {
                                if (index !in currentChoiceDifficulties) {
                                    currentChoiceDifficulties.add(index)
                                } else {
                                    currentChoiceDifficulties.remove(index)
                                }
                            }
                        )
                    }
                }
            }
            Row {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onRequest(currentChoiceDifficulties.sorted())
                        onDismissRequest()
                    }
                ) {
                    Text("确认")
                }

                TextButton(
                    onClick = {
                        onDismissRequest()
                    }
                ) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun <T> MultiObjectSelectDialog(
    onRequest: (T) -> Unit,
    onDismiss: () -> Unit,
    objects: List<T>,
    objectNameList: List<String> = objects.map { it.toString() }
) {
    BasicAlertDialog(
        modifier = Modifier.wrapContentSize(),
        onDismissRequest = onDismiss,
    ) {
        Card(
            modifier = Modifier.padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = getCardColor())
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("选择要选择的项")
                HorizontalDivider()
                objectNameList.forEachIndexed { index, item ->
                    TextButtonItem(
                        modifier = Modifier.fillMaxWidth().height(30.dp),
                        title = item,
                        onClick = {
                            onRequest(objects[index])
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CheckUpdateDialog(
    release: Release?,
    onRequest: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = { onRequest() }) {
        Card(
            modifier = Modifier
                .sizeIn(maxWidth = 300.dp, minHeight = 200.dp)
                .wrapContentSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(getCardColor())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (release != null) """
                        检测到新版本(${release.tagName}), 是否下载并安装?
                        新版本发布时间: ${
                            OffsetDateTime.parse(release.createdAt)
                                .atZoneSameInstant(ZoneId.systemDefault())
                                .toLocalDateTime()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }
                        更新内容大小: ${release.assets.first().size / 1024 / 1024} MB
                        """.trimIndent() else "已经是最新版本",
                    modifier = Modifier.padding(16.dp)
                )

                if (release != null && release.body.isNotEmpty()) {

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(16.dp)
                            .background(getCardColor().darken(0.8f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth()
                                .fillMaxHeight(),
                        ) {
                            items(release.body.trimIndent().lines()) { line ->
                                Text(text = line)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (release != null) {
                        TextButton(
                            onClick = { onDismissRequest() },
                            modifier = Modifier.padding(8.dp),
                        ) {
                            Text("取消")
                        }
                    }
                    TextButton(
                        onClick = { if (release != null) onRequest() else onDismissRequest() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}

/**
 * 解除分享锁对话框（MD3 AlertDialog 规范）。
 *
 * 布局：标题 titleLarge（onSurface）→ 说明 bodyMedium（onSurfaceVariant）→
 * 口令输入框（仅导出方设了口令时显示，错误时 inline 红字提示，不用 Toast）→
 * 操作区右对齐：左「取消」、中「清除并解除」（error 色，破坏性，需二次确认）、右「确认解除」。
 *
 * @param title 对话框标题（如 "解除隐藏Rival配置"）
 * @param description 说明文案
 * @param hasCode 导出方是否设了口令；false 时隐藏口令框与「清除并解除」按钮，仅确认即可解除
 * @param clearActionName 清除解除按钮文案（如 "清除Rival配置并解除"）
 * @param clearActionHint 清除解除的后果说明（二次确认弹窗展示）
 * @param onUnlock 口令校验回调：传入输入的口令，返回是否校验通过；
 *                 未设口令（hasCode=false）时传入 ""，应直接返回 true；
 *                 返回 true 后调用方负责解除锁并关闭弹窗。
 * @param onClear 用户确认清除相关配置后回调（调用方清除数据并解除锁）
 * @param onDismiss 关闭弹窗（取消）
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun UnlockDialog(
    title: String,
    description: String,
    hasCode: Boolean,
    clearActionName: String = "",
    clearActionHint: String = "",
    onUnlock: (String) -> Boolean,
    onClear: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }
    var codeHidden by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        ConfirmDialog(
            info = "$clearActionName？\n$clearActionHint",
            onRequest = {
                showClearConfirm = false
                onClear()
            },
            onDismiss = { showClearConfirm = false }
        )
        return
    }

    BasicAlertDialog(
        modifier = Modifier.wrapContentSize(),
        onDismissRequest = onDismiss,
    ) {
        Card(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = getCardColor())
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (hasCode) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = it
                            codeError = false
                        },
                        singleLine = true,
                        isError = codeError,
                        label = { Text("解除口令") },
                        visualTransformation = if (codeHidden) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        trailingIcon = {
                            IconButton(onClick = { codeHidden = !codeHidden }) {
                                Icon(
                                    painterResource(
                                        if (codeHidden) R.drawable.visibility_24px
                                        else R.drawable.visibility_off_24px
                                    ),
                                    contentDescription = if (codeHidden) "显示口令" else "隐藏口令",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        supportingText = {
                            // MD3：supportingText 区域常驻占位，避免错误提示出现/消失时输入框高度抖动
                            Text(
                                text = if (codeError) "口令错误，无法解除" else " ",
                                color = if (codeError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0f)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    if (hasCode) {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(clearActionName)
                        }
                    }
                    TextButton(
                        onClick = {
                            if (hasCode) {
                                if (onUnlock(code.trim())) {
                                    onDismiss()
                                } else {
                                    codeError = true
                                }
                            } else {
                                onUnlock("")
                                onDismiss()
                            }
                        }
                    ) {
                        Text("确认解除")
                    }
                }
            }
        }
    }
}
