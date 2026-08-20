package io.github.teriver.maiupload.ui.compose.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

object SyncViewModel : ViewModel() {
    var openInitDialog by mutableStateOf(false)
    var openInitDownloadDialog by mutableStateOf(false)
    var tokenHidden by mutableStateOf(true)
    var downloadComplateMethod by mutableStateOf({})

    /**
     * Token 输入方式：0 = 直接输入 Token，1 = OAuth 授权流程。
     * 仅对落雪查分器（LXNS）生效。默认 OAuth（1）。
     */
    var tokenInputMode by mutableStateOf(1)

    /**
     * 水鱼 Token 输入方式：0 = 直接输入 Import-Token，1 = OAuth 授权流程。
     * 独立于落雪的 tokenInputMode（各自记住自己的模式）。
     * 默认 OAuth（1），与水鱼 OAuth 迁移方向一致；需要 Import-Token 时可手动切回 Token。
     */
    var divingfishTokenInputMode by mutableStateOf(1)
}