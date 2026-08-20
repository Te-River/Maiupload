package io.github.teriver.maiupload.core.prober.divingfish

import android.util.Base64
import io.github.teriver.maiupload.Application.Companion.application
import io.github.teriver.maiupload.BuildConfig
import io.github.teriver.maiupload.core.prober.client
import io.github.teriver.maiupload.core.prober.sendMessageToUi
import io.github.teriver.maiupload.core.utils.DebugLog
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.max

/** 水鱼账号 OAuth userinfo 响应（openid scope 下可用）。 */
@Serializable
data class DivingFishOAuthUserInfo(
    val sub: String = "",
    val preferred_username: String = "",
    val name: String = "",
    val nickname: String = ""
)

/**
 * 水鱼查分器 OAuth 2.0 + PKCE 接入工具（公开客户端，无 client_secret）。
 *
 * 流程（本地回调方案，redirect_uri = 127.0.0.1:8284，无需公网服务器）：
 *  1. [getAuthorizeUrl] 生成随机 code_verifier + state 存入 ConfigStorage，
 *     计算 code_challenge (S256)，拼好带 PKCE/state 参数的授权链接；
 *  2. 用户在浏览器授权，浏览器跳回 `http://127.0.0.1:8284/divingfish/oauth/callback`
 *     （由 App 本地 HttpServer 承接，见 HttpServer.java），携带 code + state；
 *  3. [exchangeCodeForToken] 校验 state 后用 授权码 + code_verifier 换
 *     access_token / refresh_token（PKCE 强制，S256）；
 *  4. 之后 API 调用前调 [ensureValidAccessToken]，过期则用 refresh_token 自动刷新
 *     （水鱼 refresh token 强制轮换：每次刷新签发新 token，旧 token 立即作废）。
 *
 * client_id 来自 BuildConfig（公开标识，反编译拿到也无法冒充本应用授权）；
 * 公开客户端换 token 不传 client_secret，PKCE 保证授权码即使被截获也无法换 token。
 */
object DivingFishOAuthUtil {
    private const val TAG = "DivingFishOAuthUtil"

    /** 本地回调地址：必须与开发者控制台登记的 redirect_uri 逐字符一致（无尾斜杠）。 */
    const val REDIRECT_URI = "http://127.0.0.1:8284/divingfish/oauth/callback"

    /** 申请的权限范围：读取/写入舞萌与中二节奏成绩 + 用户资料（写入 scope 需人工审核）。 */
    private const val SCOPE =
        "openid profile prober.records.read prober.records.write " +
            "chunithm.records.read chunithm.records.write"

    private const val AUTH_SERVER = "https://auth.diving-fish.com"
    private const val AUTHORIZE_URL = "$AUTH_SERVER/oauth/authorize"
    private const val TOKEN_URL = "$AUTH_SERVER/oauth/token"

    /** access_token 提前刷新的缓冲（秒）：剩余 ≤2min 即强制刷新，避免临界过期拿到就过期。 */
    private const val REFRESH_BUFFER_SECONDS = 120L

    /** PKCE code_verifier 长度（RFC 7636 推荐 43-128 字符，64 字节 base64url 约 86 字符）。 */
    private const val CODE_VERIFIER_BYTES = 64

    private val json = Json { ignoreUnknownKeys = true }

    /** OAuth token 响应体（顶层字段，符合 OAuth 2.0 标准）。 */
    @Serializable
    private data class TokenResponse(
        val access_token: String = "",
        val token_type: String = "",
        val expires_in: Int = 0,
        val refresh_token: String = "",
        val scope: String = ""
    )

    /** 错误响应体（扁平格式）。 */
    @Serializable
    private data class TokenErrorResponse(
        val error: String = "",
        val error_description: String? = null
    )

    /** 生成 PKCE code_verifier（高熵随机字符串，SecureRandom + base64url 无 padding）。 */
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(CODE_VERIFIER_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    /** 从 code_verifier 计算 code_challenge (S256)：base64url( SHA256(verifier) ) 无 padding。 */
    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    /** 生成随机 state（CSRF 防护，回调时原样校验）。 */
    private fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    /**
     * 拼好带 PKCE + state 参数的授权链接，code_verifier / state 存入 ConfigStorage，
     * 回调换 token 时取出校验。client_id 若为占位符/空则前置拦截给清晰提示。
     */
    fun getAuthorizeUrl(): String {
        val clientId = BuildConfig.DF_OAUTH_CLIENT_ID
        if (clientId.isBlank() || clientId.equals("placeholder", ignoreCase = true)) {
            sendMessageToUi("本构建未配置水鱼 OAuth client_id，请用正式构建版本")
            return ""
        }
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = generateState()

        // 存 code_verifier + state，exchangeCodeForToken 时取出校验
        val cfg = application.configManager.config
        cfg.divingfishOAuthPkceVerifier = codeVerifier
        cfg.divingfishOAuthState = state
        application.configManager.save()

        val scopeEncoded = SCOPE.replace(" ", "+")
        val redirectEncoded = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return "$AUTHORIZE_URL?response_type=code" +
            "&client_id=$clientId" +
            "&redirect_uri=$redirectEncoded" +
            "&scope=$scopeEncoded" +
            "&state=$state" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=S256"
    }

    /**
     * 用授权码 + code_verifier 换 access_token / refresh_token（PKCE，无需 client_secret）。
     * 先校验回调携带的 state（CSRF），成功后存入 ConfigStorage 并清空 code_verifier/state。
     * 若用户在设置页填了 client_secret（机密客户端登记方式），换 token 时自动按
     * client_secret_post 附加，见 [postTokenAndStore]。
     */
    suspend fun exchangeCodeForToken(code: String, state: String): Boolean {
        if (code.isBlank()) {
            sendMessageToUi("授权码不能为空")
            return false
        }
        val clientId = BuildConfig.DF_OAUTH_CLIENT_ID
        if (clientId.isBlank() || clientId.equals("placeholder", ignoreCase = true)) {
            sendMessageToUi("本构建未配置水鱼 OAuth client_id，请用正式构建版本")
            return false
        }
        val cfg = application.configManager.config
        if (cfg.divingfishOAuthState.isNotEmpty() && state != cfg.divingfishOAuthState) {
            sendMessageToUi("state 校验失败，已丢弃本次回调，请重新授权")
            return false
        }
        val codeVerifier = cfg.divingfishOAuthPkceVerifier
        if (codeVerifier.isBlank()) {
            sendMessageToUi("PKCE 验证码丢失，请重新授权")
            return false
        }
        val body = "grant_type=authorization_code" +
            "&client_id=$clientId" +
            "&code=${URLEncoder.encode(code, "UTF-8")}" +
            "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
            "&code_verifier=$codeVerifier"
        val ok = postTokenAndStore(body, hint = "授权码换令牌")
        if (ok) {
            // code_verifier/state 一次性使用，换 token 后立即清空
            cfg.divingfishOAuthPkceVerifier = ""
            cfg.divingfishOAuthState = ""
            application.configManager.save()
        }
        return ok
    }

    /**
     * 确保 access_token 仍有效：未过期直接返回；过期且有 refresh_token 则尝试刷新；
     * 刷新失败返回 null，**不清空本地令牌**，由 API 实际报错时提示重新绑定。
     */
    suspend fun ensureValidAccessToken(force: Boolean = false): String? {
        val cfg = application.configManager.config
        val now = System.currentTimeMillis()
        if (!force && cfg.divingfishOAuthAccessToken.isNotEmpty() &&
            cfg.divingfishOAuthAccessTokenExpireAt > now + REFRESH_BUFFER_SECONDS * 1000
        ) {
            return cfg.divingfishOAuthAccessToken
        }
        val clientId = BuildConfig.DF_OAUTH_CLIENT_ID
        if (clientId.isBlank() || clientId.equals("placeholder", ignoreCase = true)) {
            sendMessageToUi("本构建未配置水鱼 OAuth client_id，无法刷新令牌，请用正式构建版本")
            return null
        }
        if (cfg.divingfishOAuthRefreshToken.isBlank()) {
            return null
        }
        val body = "grant_type=refresh_token" +
            "&client_id=$clientId" +
            "&refresh_token=${URLEncoder.encode(cfg.divingfishOAuthRefreshToken, "UTF-8")}"
        val ok = postTokenAndStore(body, hint = "刷新令牌")
        return if (ok) application.configManager.config.divingfishOAuthAccessToken else null
    }

    /** 清空本地 OAuth 令牌（用户取消授权时调用）。 */
    fun clearTokens() {
        val cfg = application.configManager.config
        cfg.divingfishOAuthAccessToken = ""
        cfg.divingfishOAuthRefreshToken = ""
        cfg.divingfishOAuthAccessTokenExpireAt = 0
        cfg.divingfishOAuthPkceVerifier = ""
        cfg.divingfishOAuthState = ""
        application.configManager.save()
    }

    /** 本地是否已有 OAuth 授权（有 refresh_token 即视为已授权过）。 */
    fun isAuthorized(): Boolean =
        application.configManager.config.divingfishOAuthRefreshToken.isNotEmpty()

    /**
     * 本地回调入口（HttpServer.java 调用）：解析 code/state 后换 token，
     * 返回给浏览器展示的 HTML 页面。@JvmStatic 供 Java 侧直接调用。
     */
    @JvmStatic
    fun handleLocalCallback(code: String?, state: String?): String {
        val ok = runBlocking { exchangeCodeForToken(code.orEmpty(), state.orEmpty()) }
        return if (ok) {
            "<html><body><h1>水鱼授权成功，可关闭本页面并返回 App</h1></body></html>"
        } else {
            "<html><body><h1>水鱼授权失败，请返回 App 重试</h1></body></html>"
        }
    }

    // ---- 内部 ----

    /**
     * POST token 端点（form-urlencoded）。默认按公开客户端调用（不传 client_secret，PKCE 保证安全）；
     * 若用户填了 client_secret（控制台按机密客户端登记），自动按 client_secret_post 方式附加，
     * 兼容两种登记方式。成功即把 access/refresh token 落盘；水鱼 refresh token 强制轮换，
     * 响应里的新 refresh_token 必须先持久化，旧 token 立即作废。
     */
    private suspend fun postTokenAndStore(
        body: String,
        hint: String
    ): Boolean {
        // 水鱼服务器实测：该 client_id 按机密客户端登记，公开客户端（无 secret）会被拒
        // （invalid_client: cannot authenticate with methods 含 none）。
        // 取 secret 优先级：用户在设置页填的配置字段 > BuildConfig 默认值。
        val secret = application.configManager.config.divingfishOAuthClientSecret
            .ifBlank { BuildConfig.DF_OAUTH_CLIENT_SECRET }
        val finalBody = if (secret.isNotBlank()) {
            "$body&client_secret=${URLEncoder.encode(secret, "UTF-8")}"
        } else {
            body
        }
        return try {
            val resp = client.post(TOKEN_URL) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(finalBody)
            }
            val respText = resp.bodyAsText()
            if (resp.status.value != 200) {
                val err = try {
                    json.decodeFromString<TokenErrorResponse>(respText)
                } catch (_: Exception) {
                    null
                }
                val msg = err?.let { "${it.error}${it.error_description?.let { d -> ": $d" } ?: ""}" }
                    ?: respText
                DebugLog.log("E", TAG, "$hint 失败: $msg")
                sendMessageToUi("${hint}失败: $msg")
                false
            } else {
                val token = json.decodeFromString<TokenResponse>(respText)
                val cfg = application.configManager.config
                cfg.divingfishOAuthAccessToken = token.access_token
                // 水鱼强制轮换 refresh_token：先落盘新 token，旧的作废
                if (token.refresh_token.isNotEmpty()) {
                    cfg.divingfishOAuthRefreshToken = token.refresh_token
                }
                cfg.divingfishOAuthAccessTokenExpireAt = System.currentTimeMillis() +
                    max(token.expires_in - REFRESH_BUFFER_SECONDS, 0L) * 1000
                application.configManager.save()
                DebugLog.log("D", TAG, "$hint 成功，access_token 有效期 ${token.expires_in}s")
                true
            }
        } catch (e: Exception) {
            DebugLog.log("E", TAG, "$hint 异常: ${e.message}", e)
            sendMessageToUi("${hint}异常: ${e.message}")
            false
        }
    }
}
