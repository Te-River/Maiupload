package io.github.teriver.maiupload.ui.compose.setting

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.viewModelScope
import io.github.teriver.maiupload.Application.Companion.application
import io.github.teriver.maiupload.BuildConfig
import io.github.teriver.maiupload.core.config.ConfigStorage
import io.github.teriver.maiupload.core.config.ConfigTransfer
import io.github.teriver.maiupload.core.prober.rival.RivalSyncUtil
import io.github.teriver.maiupload.GlobalViewModel
import io.github.teriver.maiupload.core.config.ScoreDisplayType
import io.github.teriver.maiupload.core.config.ScoreStyleType
import io.github.teriver.maiupload.core.data.chuni.ChuniEnums
import io.github.teriver.maiupload.core.data.maimai.MaimaiEnums
import io.github.teriver.maiupload.core.prober.sendMessageToUi
import io.github.teriver.maiupload.core.utils.checkFullUpdate
import io.github.teriver.maiupload.core.utils.checkReleaseUpdate
import io.github.teriver.maiupload.ui.component.ConfirmDialog
import io.github.teriver.maiupload.ui.component.DiffChooseDialog
import io.github.teriver.maiupload.ui.component.DownloadDialog
import io.github.teriver.maiupload.ui.component.MultiObjectSelectDialog
import io.github.teriver.maiupload.ui.component.UnlockDialog
import io.github.teriver.maiupload.ui.component.WindowInsetsSpacer
import io.github.teriver.maiupload.ui.compose.scores.resources
import io.github.teriver.maiupload.ui.compose.setting.components.ScoreDisplayExampleLarge
import io.github.teriver.maiupload.ui.compose.setting.components.ScoreDisplayExampleMiddle
import io.github.teriver.maiupload.ui.compose.setting.components.ScoreDisplayExampleSmall
import io.github.teriver.maiupload.ui.compose.setting.components.SettingScoreStyleExampleColorOverlay
import io.github.teriver.maiupload.ui.compose.setting.components.SettingScoreStyleExampleTextShadow
import io.github.teriver.maiupload.ui.theme.getCardColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingCompose() {
    val maimaiShougouColorList = listOf("normal", "bronze", "silver", "gold", "rainbow")

    val config = application.configManager.config
    
    var divingfishToken by remember { mutableStateOf(config.divingfishToken) }
    var lxnsToken by remember { mutableStateOf(config.lxnsToken) }

    var userName by remember { mutableStateOf(config.userInfo.name) }
    var maimaiIcon by remember { mutableStateOf(config.userInfo.maimaiIcon.toString()) }
    var maimaiPlate by remember { mutableStateOf(config.userInfo.maimaiPlate.toString()) }
    var maimaiShougouText by remember { mutableStateOf(config.userInfo.shougou) }
    var maimaiShougouColor by remember { mutableStateOf(config.userInfo.shougouColor) }

    var divingfishTokenHidden by remember { mutableStateOf(true) }
    var lxnsTokenHidden by remember { mutableStateOf(true)}

    var scoreDisplayType by remember { mutableStateOf(config.scoreDisplayType) }
    var scoreColorOverlayType by remember { mutableStateOf(config.scoreStyleType) }

    var showChooseMaimaiDiffDialog by remember { mutableStateOf(false) }
    var showChooseChuniDiffDialog by remember { mutableStateOf(false) }
    var showSelectShougouColorDialog by remember { mutableStateOf(false) }

    var showRivalSetting by remember { mutableStateOf(false) }

    // 导出配置对话框：分享锁选项（隐藏Rival配置 / 禁止二次分享）+ 各自选填解除口令
    var showExportConfigDialog by remember { mutableStateOf(false) }
    var exportHideRival by remember { mutableStateOf(false) }
    var exportNoReshare by remember { mutableStateOf(false) }
    var exportRivalUnlockCode by remember { mutableStateOf("") }
    var exportNoReshareUnlockCode by remember { mutableStateOf("") }
    // 解除「禁止二次分享」锁
    var showUnlockNoReshareDialog by remember { mutableStateOf(false) }

    var showConfirmUpdateSongResourceDialog by remember { mutableStateOf(false) }
    var showUpdateSongResourceDialog by remember { mutableStateOf(false) }

    val groupPadding = PaddingValues(15.dp)

    when {
        showChooseMaimaiDiffDialog -> {
            DiffChooseDialog(
                onRequest = {
                    config.syncConfig.maimaiSyncDifficulty = it
                    application.configManager.save()
                },
                onDismissRequest = {
                    showChooseMaimaiDiffDialog = false
                },
                defaultList = MaimaiEnums.Difficulty.entries.map { it.diffName },
                currentChoiceList = config.syncConfig.maimaiSyncDifficulty,
            )
        }
        showChooseChuniDiffDialog -> {
            DiffChooseDialog(
                onRequest = {
                    config.syncConfig.chuniSyncDifficulty = it
                    application.configManager.save()
                },
                onDismissRequest = {
                    showChooseChuniDiffDialog = false
                },
                defaultList = ChuniEnums.Difficulty.entries.map { it.diffName },
                currentChoiceList = config.syncConfig.chuniSyncDifficulty,
            )
        }
        showConfirmUpdateSongResourceDialog -> {
            ConfirmDialog(
                info = "是否确认更新资源",
                onRequest = {
                    showUpdateSongResourceDialog = true
                },
                onDismiss = {
                    showConfirmUpdateSongResourceDialog = false
                }
            )
        }
        showUpdateSongResourceDialog -> {
            DownloadDialog(
                resources
            ) {
                showUpdateSongResourceDialog = false
                sendMessageToUi("更新完成")
            }
        }
        showSelectShougouColorDialog -> {
            MultiObjectSelectDialog(
                onRequest = {
                    maimaiShougouColor = it
                    config.userInfo.shougouColor = it
                    application.configManager.save()
                },
                onDismiss = {
                    showSelectShougouColorDialog = false
                },
                objects = maimaiShougouColorList,
            )
        }
        showExportConfigDialog -> {
            val exportContext = LocalContext.current
            ExportConfigDialog(
                hideRival = exportHideRival,
                noReshare = exportNoReshare,
                rivalUnlockCode = exportRivalUnlockCode,
                noReshareUnlockCode = exportNoReshareUnlockCode,
                onHideRivalChange = { exportHideRival = it },
                onNoReshareChange = { exportNoReshare = it },
                onRivalUnlockCodeChange = { exportRivalUnlockCode = it },
                onNoReshareUnlockCodeChange = { exportNoReshareUnlockCode = it },
                onExport = {
                    showExportConfigDialog = false
                    GlobalViewModel.viewModelScope.launch(Dispatchers.IO) {
                        val path = ConfigTransfer.exportToFile(
                            hideRivalConfig = exportHideRival,
                            noReshare = exportNoReshare,
                            rivalUnlockCode = exportRivalUnlockCode,
                            noReshareUnlockCode = exportNoReshareUnlockCode,
                        )
                        withContext(Dispatchers.Main) {
                            if (path.isNotEmpty()) {
                                val file = java.io.File(path)
                                val uri = FileProvider.getUriForFile(
                                    application,
                                    application.packageName + ".fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TITLE, "Maiupload 配置")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                exportContext.startActivity(
                                    Intent.createChooser(shareIntent, "分享配置文件").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } else {
                                sendMessageToUi("导出失败")
                            }
                        }
                    }
                },
                onDismiss = { showExportConfigDialog = false }
            )
        }
        showUnlockNoReshareDialog -> {
            UnlockDialog(
                title = "解除禁止二次分享",
                description = "输入导出方提供的解除口令；忘记口令可清除相关配置解除（将恢复默认设置）。",
                hasCode = config.noReshareUnlockCodeHash.isNotEmpty() || config.noReshareUnlockData.isNotEmpty(),
                clearActionName = "清除并解除",
                clearActionHint = "将恢复所有设置为默认值（含 Token 等），此操作不可撤销",
                onUnlock = { input ->
                    // 双通道校验：SHA-256 哈希或加密字段（以口令 hash 为准解密）任一通过即可
                    val ok = ConfigTransfer.verifyUnlockCode(input, config.noReshareUnlockCodeHash) ||
                        ConfigTransfer.verifyUnlockData(input, config.noReshareUnlockData)
                    if (ok) {
                        config.noReshare = false
                        config.noReshareUnlockCodeHash = ""
                        config.noReshareUnlockData = ""
                        application.configManager.save()
                        sendMessageToUi("已解除禁止二次分享")
                    }
                    ok
                },
                onClear = {
                    // 清除相关配置项：就地恢复默认值（保持本地 config 引用有效），并解除锁
                    val fresh = ConfigStorage()
                    config.divingfishToken = fresh.divingfishToken
                    config.lxnsToken = fresh.lxnsToken
                    config.lxnsOAuthAccessToken = fresh.lxnsOAuthAccessToken
                    config.lxnsOAuthRefreshToken = fresh.lxnsOAuthRefreshToken
                    config.lxnsOAuthAccessTokenExpireAt = fresh.lxnsOAuthAccessTokenExpireAt
                    config.lxnsOAuthPkceVerifier = fresh.lxnsOAuthPkceVerifier
                    config.rivalSyncConfig = fresh.rivalSyncConfig
                    config.syncConfig = fresh.syncConfig
                    config.localConfig = fresh.localConfig
                    config.userInfo = fresh.userInfo
                    config.scoreDisplayType = fresh.scoreDisplayType
                    config.scoreStyleType = fresh.scoreStyleType
                    config.lxnsRomVersionThreshold = fresh.lxnsRomVersionThreshold
                    config.hideRivalConfig = false
                    config.noReshare = false
                    config.rivalUnlockCodeHash = ""
                    config.noReshareUnlockCodeHash = ""
                    config.rivalUnlockData = ""
                    config.noReshareUnlockData = ""
                    application.configManager.save()
                    sendMessageToUi("已清除配置并解除禁止二次分享")
                },
                onDismiss = { showUnlockNoReshareDialog = false }
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 二级菜单跳转过渡动画（Material Motion 规范：300ms FastOutSlowInEasing）
        AnimatedContent(
            targetState = showRivalSetting,
            transitionSpec = {
                if (targetState) {
                    // 进入 Rival 设置：从右滑入 + 淡入，旧页左移淡出
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(300)))
                } else {
                    // 返回设置主页：从左滑入 + 淡入，旧页右移淡出
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(300)))
                }
            },
            label = "RivalSettingTransition"
        ) { showRival ->
        if (showRival) {
            RivalSettingCompose(onBack = { showRivalSetting = false })
        } else {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(if (application.isLandscape) 2 else 1),
            modifier = Modifier.fillMaxSize()
        ) {
            item(
                span = StaggeredGridItemSpan.FullLine
            ) {
                WindowInsetsSpacer.TopPaddingSpacer()
            }
            item {
                SettingItemGroup(
                    modifier = Modifier
                        .padding(groupPadding)
                        .wrapContentSize(),
                    title = "查分Token设置"
                ) {
                    PasswordTextFiled(
                        modifier = Modifier
                            .padding(15.dp)
                            .fillMaxWidth()
                            .height(60.dp),
                        label = { Text("水鱼查分器Token") },
                        icon = { Icon(Icons.Filled.Lock, null) },
                        hidden = divingfishTokenHidden,
                        value = divingfishToken,
                        onTrailingIconClick = {
                            divingfishTokenHidden = !divingfishTokenHidden
                        },
                        onValueChange = {
                            divingfishToken = it
                            config.divingfishToken = it
                            application.configManager.save()
                        }
                    )

                    PasswordTextFiled(
                        modifier = Modifier
                            .padding(15.dp)
                            .fillMaxWidth()
                            .height(60.dp),
                        label = { Text("落雪查分器Token") },
                        icon = { Icon(Icons.Filled.Lock, null) },
                        hidden = lxnsTokenHidden,
                        value = lxnsToken,
                        onTrailingIconClick = {
                            lxnsTokenHidden = !lxnsTokenHidden
                        },
                        onValueChange = {
                            lxnsToken = it
                            config.lxnsToken = it
                            application.configManager.save()
                        }
                    )
                }
            }
            item {
                SettingItemGroup(
                    modifier = Modifier
                        .padding(groupPadding)
                        .wrapContentSize(),
                    title = "成绩抓取设置"
                ) {
                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "Rival 设置",
                        description = "类型一（Rival 同步）的鉴权网址 / 服务器网址 / 加密参数等"
                    ) {
                        showRivalSetting = true
                    }
                    SwitchSettingItem(
                        title = "增量抓取舞萌成绩",
                        description = "每次爬取时使用的爬取方式，增量爬取依赖最近游玩记录，适合已经完整爬取后频繁爬取，更加稳定",
                        checked = config.syncConfig.maimaiIncrementalFetchScore,
                        onCheckedChange = {
                            config.syncConfig.maimaiIncrementalFetchScore = it
                            application.configManager.save()
                        }
                    )

                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "同步舞萌DX成绩的难度",
                        description = "选择后将只同步选择的难度的成绩"
                    ) {
                        showChooseMaimaiDiffDialog = true
                    }

                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "同步中二节奏成绩的难度",
                        description = "选择后将只同步选择的难度的成绩"
                    ) {
                        showChooseChuniDiffDialog = true
                    }
                }
            }
            item {
                SettingItemGroup(
                    modifier = Modifier
                        .padding(groupPadding)
                        .wrapContentSize(),
                    title = "成绩展示设置"
                ) {
                    BaseTextItem(
                        title = "成绩卡片排列",
                        modifier = Modifier
                            .padding(start = 16.dp, top = 8.dp)
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        Arrangement.SpaceAround,
                        Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    scoreDisplayType = ScoreDisplayType.Small
                                    config.scoreDisplayType = ScoreDisplayType.Small
                                    application.configManager.save()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ScoreDisplayExampleSmall(
                                Modifier
                                    .padding(4.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    ScoreDisplayType.Small.displayName,
                                )
                                RadioButton(
                                    selected = scoreDisplayType == ScoreDisplayType.Small,
                                    onClick = null
                                )
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    scoreDisplayType = ScoreDisplayType.Middle
                                    config.scoreDisplayType = ScoreDisplayType.Middle
                                    application.configManager.save()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ScoreDisplayExampleMiddle(
                                Modifier
                                    .padding(4.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    ScoreDisplayType.Middle.displayName,
                                )
                                RadioButton(
                                    selected = scoreDisplayType == ScoreDisplayType.Middle,
                                    onClick = null
                                )
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    scoreDisplayType = ScoreDisplayType.Large
                                    config.scoreDisplayType = ScoreDisplayType.Large
                                    application.configManager.save()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ScoreDisplayExampleLarge(
                                Modifier
                                    .padding(4.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    ScoreDisplayType.Large.displayName,
                                )
                                RadioButton(
                                    selected = scoreDisplayType == ScoreDisplayType.Large,
                                    onClick = null
                                )
                            }
                        }
                    }

                    horizontalDivider()

                    BaseTextItem(
                        title = "成绩卡片样式",
                        modifier = Modifier
                            .padding(start = 16.dp, top = 8.dp)
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        Arrangement.SpaceAround,
                        Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.large)
                                .clickable {
                                    scoreColorOverlayType = ScoreStyleType.ColorOverlay
                                    config.scoreStyleType = ScoreStyleType.ColorOverlay
                                    application.configManager.save()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SettingScoreStyleExampleColorOverlay(
                                Modifier
                                    .padding(4.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    ScoreStyleType.ColorOverlay.displayName,
                                )
                                RadioButton(
                                    selected = scoreColorOverlayType == ScoreStyleType.ColorOverlay,
                                    onClick = null
                                )
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.large)
                                .clickable {
                                    scoreColorOverlayType = ScoreStyleType.TextShadow
                                    config.scoreStyleType = ScoreStyleType.TextShadow
                                    application.configManager.save()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SettingScoreStyleExampleTextShadow(
                                Modifier
                                    .padding(4.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    ScoreStyleType.TextShadow.displayName,
                                )
                                RadioButton(
                                    selected = scoreColorOverlayType == ScoreStyleType.TextShadow,
                                    onClick = null
                                )
                            }
                        }
                    }

                    horizontalDivider()
                }
            }
            item {
                SettingItemGroup(
                    modifier = Modifier
                        .padding(groupPadding)
                        .wrapContentSize(),
                    title = "本地设置"
                ) {
                    SwitchSettingItem(
                        title = "自动检测更新",
                        description = "自动检测更新，发现新版本后将会在启动时提醒",
                        checked = config.localConfig.checkUpdate,
                        onCheckedChange = {
                            config.localConfig.checkUpdate = it
                            application.configManager.save()
                        }
                    )

                    SwitchSettingItem(
                        title = "检测Snapshot更新",
                        description = "开启后将会检测Snapshot的更新，若关闭则只会检测Release更新",
                        checked = config.localConfig.checkSnapshotUpdate,
                        onCheckedChange = {
                            config.localConfig.checkSnapshotUpdate = it
                            application.configManager.save()
                        }
                    )

                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "更新歌曲信息与别名",
                        description = "会从Lxns的API获取舞萌和中二歌曲信息与别名并覆盖本地文件"
                    ) {
                        showConfirmUpdateSongResourceDialog = true
                    }

                    SwitchSettingItem(
                        title = "成绩缓存本地",
                        description = "开启后, 抓取成绩并上传到查分器时会缓存此次查分成绩到本地",
                        checked = config.localConfig.cacheScore,
                        onCheckedChange = {
                            config.localConfig.cacheScore = it
                            application.configManager.save()
                        }
                    )

                    SwitchSettingItem(
                        title = "解析舞萌DX用户信息",
                        description = "开启后, 抓取舞萌DX成绩时会解析用户信息并保存",
                        checked = config.localConfig.parseMaimaiUserInfo,
                        onCheckedChange = {
                            config.localConfig.parseMaimaiUserInfo = it
                            application.configManager.save()
                        }
                    )
                }
            }
            item {
                SettingItemGroup(
                    modifier = Modifier
                        .padding(groupPadding)
                        .wrapContentSize(),
                    title = "用户信息"
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth(),
                        value = userName,
                        onValueChange = {
                            config.userInfo.name = it
                            userName = it
                            application.configManager.save()
                        },
                        label = { Text("用户名", fontSize = 12.sp) },
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth(),
                        value = maimaiIcon.toString(),
                        onValueChange = {
                            if (it.isDigitsOnly() && it.isNotEmpty()) {
                                config.userInfo.maimaiIcon = it.toInt()
                                application.configManager.save()
                                maimaiIcon = it
                            } else if (it.isEmpty()) {
                                config.userInfo.maimaiIcon = 1
                                application.configManager.save()
                                maimaiIcon = it
                            }
                        },
                        label = { Text("舞萌DX头像", fontSize = 12.sp) },
                        supportingText = {
                            Text("*不知道该参数的含义，请勿修改", color = MaterialTheme.colorScheme.error)
                        }
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth(),
                        value = maimaiPlate.toString(),
                        onValueChange = {
                            if (it.isDigitsOnly() && it.isNotEmpty()) {
                                config.userInfo.maimaiPlate = it.toInt()
                                application.configManager.save()
                                maimaiPlate = it
                            } else if (it.isEmpty()) {
                                config.userInfo.maimaiPlate = 1
                                application.configManager.save()
                                maimaiPlate = it
                            }
                        },
                        label = { Text("舞萌DX姓名框", fontSize = 12.sp) },
                        supportingText = {
                            Text("*不知道该参数的含义，请勿修改", color = MaterialTheme.colorScheme.error)
                        }
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth(),
                        value = maimaiShougouText,
                        onValueChange = {
                            config.userInfo.shougou = it
                            maimaiShougouText = it
                            application.configManager.save()
                        },
                        label = { Text("称号框内容", fontSize = 12.sp) },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )

                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "选择称号框颜色 (当前: $maimaiShougouColor)",
                        onClick = {
                            showSelectShougouColorDialog = true
                        }
                    )

                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "打开资源ID对照表",
                        description = "点击后将跳转到本APP使用的资源ID对照页面"
                    ) {
                        val uri = Uri.parse("https://rif.skydynamic.top")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        application.startActivity(intent)
                    }
                }
            }
            item {
                SettingItemGroup(
                    modifier = Modifier
                        .padding(groupPadding)
                        .wrapContentSize(),
                    title = "其他"
                ) {
                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "清除缓存",
                        description = "清除APP产生的缓存"
                    ) {
                        val clearSize = application.clearCache()
                        sendMessageToUi(
                            "清除缓存成功, 释放了${clearSize / 1024 / 1024}MB缓存",
                        )
                    }
                    if (config.noReshare) {
                        TextButtonItem(
                            modifier = Modifier
                                .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            title = "解除禁止二次分享",
                            description = "该配置禁止二次分享；输入口令或清除相关配置后可恢复导出"
                        ) {
                            showUnlockNoReshareDialog = true
                        }
                    }
                }
            }
            item {
                SettingItemGroup(
                    modifier = Modifier
                        .padding(groupPadding)
                        .wrapContentSize(),
                    title = "关于"
                ) {
                    Text(
                        "App版本: ${BuildConfig.VERSION_NAME}-${BuildConfig.BUILD_TYPE}",
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(15.dp, top = 0.dp, bottom = 0.dp)
                    )

                    HorizontalDivider(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )

                    // 导出配置：写入 filesDir，用 ACTION_SEND 分享给他人（禁止二次分享时拦截）
                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "导出配置",
                        description = "导出成绩抓取/展示/本地设置与用户信息（不含Userid），可选隐藏Rival/禁止二次分享"
                    ) {
                        if (config.noReshare) {
                            // 禁止二次分享锁生效：直接弹出解除对话框（口令校验或清除配置解除）
                            showUnlockNoReshareDialog = true
                        } else {
                            showExportConfigDialog = true
                        }
                    }

                    // 导入配置：用 OpenDocument 选文件，验签后载入
                    val importContext = LocalContext.current
                    val importLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            GlobalViewModel.viewModelScope.launch(Dispatchers.IO) {
                                val result = try {
                                    val content = importContext.contentResolver.openInputStream(uri)?.use { stream ->
                                        stream.bufferedReader().readText()
                                    } ?: ""
                                    ConfigTransfer.import(content)
                                } catch (e: Exception) {
                                    ConfigTransfer.ImportResult.Corrupted
                                }
                                withContext(Dispatchers.Main) {
                                    val msg = when (result) {
                                        ConfigTransfer.ImportResult.Success -> "导入成功"
                                        is ConfigTransfer.ImportResult.VersionTooHigh ->
                                            "已导入，但注意：该配置来自更高版本的应用（v${result.bundleAppVersion}），" +
                                            "当前版本 v${BuildConfig.VERSION_NAME}，未识别的字段已自动忽略。"
                                        is ConfigTransfer.ImportResult.VersionTooNew ->
                                            "导入失败：该配置文件格式版本（v${result.bundleVersion}）高于当前应用支持的最高版本（v3），" +
                                            "请升级应用后再导入。"
                                        ConfigTransfer.ImportResult.Corrupted ->
                                            "导入失败：文件损坏或签名不符"
                                    }
                                    sendMessageToUi(msg)
                                }
                            }
                        }
                    }

                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "导入配置",
                        description = "从分享的配置文件导入设置（验签防篡改）"
                    ) {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }

                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "检查更新",
                        description = "检测Github Release是否存在更新"
                    ) {
                        GlobalViewModel.viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val release = if (config.localConfig.checkSnapshotUpdate) {
                                    checkFullUpdate()
                                } else {
                                    checkReleaseUpdate()
                                }
                                withContext(Dispatchers.Main) {
                                    if (release != null) {
                                        val downloadFile =
                                            Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS
                                            )
                                        val newVersionFile =
                                            downloadFile.resolve(release.assets.first().name)
                                        if (newVersionFile.exists()) {
                                            GlobalViewModel.newVersionApkUri =
                                                FileProvider.getUriForFile(
                                                    application,
                                                    application.packageName + ".fileprovider",
                                                    newVersionFile
                                                )
                                            GlobalViewModel.showInstallApkDialog = true
                                        } else {
                                            GlobalViewModel.setLatestReleaseAndShowDialog(release)
                                        }
                                    } else {
                                        GlobalViewModel.setLatestReleaseAndShowDialog(release)
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    GlobalViewModel.sendAndShowMessage("检查更新失败: ${e.message}")
                                }
                            }
                        }
                    }

                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "项目仓库",
                        description = "项目的GitHub仓库"
                    ) {
                        val uri = Uri.parse("https://github.com/Te-River/Maiupload")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        application.startActivity(intent)
                    }

                    TextButtonItem(
                        modifier = Modifier
                            .padding(start = 15.dp, top = 5.dp, end = 15.dp, bottom = 5.dp)
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        title = "反馈问题",
                        description = "跳转到Github Issues界面进行问题反馈"
                    ) {
                        val uri = Uri.parse("https://github.com/Te-River/Maiupload/issues")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        application.startActivity(intent)
                    }

                    Text(
                        """
                         特别感谢Lxns提供的API与优秀的成绩管理页面设计
                         也特别感谢愿意给此项目贡献的开发者与参与APP测试的朋友们
                         本项目遵循Apache LICENSE 2.0协议
                        """.trimIndent(),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(15.dp, top = 0.dp, bottom = 0.dp)
                    )
                }
            }
            item(
                span = StaggeredGridItemSpan.FullLine
            ) {
                if (application.isLandscape) {
                    WindowInsetsSpacer.BottomPaddingSpacer()
                }
            }
        }
        }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(WindowInsetsSpacer.topPadding)
                .align(Alignment.TopCenter)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
        )
    }
}

/**
 * 导出配置对话框（MD3 AlertDialog 规范）：
 * 标题 titleLarge → 说明 bodyMedium（onSurfaceVariant）→ 两个分享锁复选框行
 * （勾选后 AnimatedVisibility 展开各自选填的解除口令输入框）→ 操作区右对齐（取消/导出）。
 *
 * @param hideRival 「隐藏Rival配置」勾选态：导入方 Rival 字段（含 Userid）永久隐藏显示，同步不受影响
 * @param noReshare 「禁止二次分享」勾选态：导入方无法再次导出该配置
 * @param rivalUnlockCode / noReshareUnlockCode 各自锁的选填解除口令（留空 = 解除仅需确认）
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExportConfigDialog(
    hideRival: Boolean,
    noReshare: Boolean,
    rivalUnlockCode: String,
    noReshareUnlockCode: String,
    onHideRivalChange: (Boolean) -> Unit,
    onNoReshareChange: (Boolean) -> Unit,
    onRivalUnlockCodeChange: (String) -> Unit,
    onNoReshareUnlockCodeChange: (String) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(
        modifier = Modifier.fillMaxWidth(),
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
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "导出配置",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "所有导出文件均不含 Userid。以下选项随文件带给导入方：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 「隐藏Rival配置」复选框 + 选填解除口令
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hideRival,
                        onCheckedChange = onHideRivalChange
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Text(
                            text = "隐藏Rival配置",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "导入方 Rival 字段（含 Userid）将永久隐藏显示，同步功能不受影响",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                AnimatedVisibility(visible = hideRival) {
                    OutlinedTextField(
                        value = rivalUnlockCode,
                        onValueChange = onRivalUnlockCodeChange,
                        singleLine = true,
                        label = { Text("解除口令（选填）", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 「禁止二次分享」复选框 + 选填解除口令
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = noReshare,
                        onCheckedChange = onNoReshareChange
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Text(
                            text = "禁止二次分享",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "导入方将无法再次导出该配置",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                AnimatedVisibility(visible = noReshare) {
                    OutlinedTextField(
                        value = noReshareUnlockCode,
                        onValueChange = onNoReshareUnlockCodeChange,
                        singleLine = true,
                        label = { Text("解除口令（选填）", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    TextButton(onClick = onExport) {
                        Text("导出")
                    }
                }
            }
        }
    }
}
