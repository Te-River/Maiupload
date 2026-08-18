package io.github.teriver.maiupload.core.config

import android.content.Context
import android.util.Log
import io.github.teriver.maiupload.Application.Companion.application
import io.github.teriver.maiupload.core.data.chuni.ChuniEnums
import io.github.teriver.maiupload.core.data.maimai.MaimaiEnums
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

val JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 配置管理器：负责 [ConfigStorage] 的持久化与加密。
 *
 * 敏感字段（tokens、OAuth 令牌、Rival 鉴权参数）以 AES-GCM 加密后写入 config.json；
 * 非敏感字段（显示设置、同步难度、用户展示信息）保持明文，方便调试与人工检查。
 *
 * 加密密钥存放在 Android Keystore（应用卸载即销毁），见 [ConfigCrypto]。
 */
@OptIn(ExperimentalSerializationApi::class)
open class ConfigManager(context: Context) {
    private var configFile: File = File(context.filesDir, "config.json")

    var config: ConfigStorage = ConfigStorage()

    init {
        if (!configFile.exists()) {
            configFile.createNewFile()
            this.save()
        } else {
            this.read()
        }
    }

    private fun read() {
        val configInputStream = application.getFilesDirInputStream("config.json")
        try {
            val stored = JSON.decodeFromStream<StoredConfig>(configInputStream)
            config = stored.toConfig()
        } catch (e: Exception) {
            Log.e("ConfigManager", "读取 config.json 失败，使用默认配置: ${e.message}")
            config = ConfigStorage()
        } finally {
            configInputStream.close()
        }
    }

    fun save() {
        val configOutputStream = application.getFilesDirOutputStream("config.json")
        try {
            val stored = StoredConfig.fromConfig(config)
            JSON.encodeToStream(stored, configOutputStream)
        } catch (e: Exception) {
            Log.e("ConfigManager", "写入 config.json 失败: ${e.message}")
        } finally {
            configOutputStream.close()
        }
    }
}

/**
 * 磁盘上的配置格式：敏感字段加密，非敏感字段明文。
 *
 * 加密字段在 [fromConfig] 时经 [ConfigCrypto.encrypt] 加密，
 * 在 [toConfig] 时经 [ConfigCrypto.decrypt] 解密。
 * 字段名带 `_enc` 后缀，便于人工识别为加密字段。
 */
@Serializable
data class StoredConfig(
    // ---- 加密字段 ----
    var divingfishToken_enc: String = "",
    var lxnsToken_enc: String = "",
    var lxnsOAuthAccessToken_enc: String = "",
    var lxnsOAuthRefreshToken_enc: String = "",
    var lxnsOAuthAccessTokenExpireAt: Long = 0,
    var lxnsOAuthPkceVerifier_enc: String = "",
    var divingfishOAuthAccessToken_enc: String = "",
    var divingfishOAuthRefreshToken_enc: String = "",
    var divingfishOAuthAccessTokenExpireAt: Long = 0,
    var divingfishOAuthPkceVerifier_enc: String = "",
    var divingfishOAuthState: String = "",
    var rivalSyncConfig_enc: String = "",
    // ---- 明文字段 ----
    var syncConfig: SyncConfig = SyncConfig(),
    var localConfig: LocalConfig = LocalConfig(),
    var userInfo: UserInfo = UserInfo(),
    var scoreDisplayType: ScoreDisplayType = ScoreDisplayType.Small,
    var scoreStyleType: ScoreStyleType = ScoreStyleType.ColorOverlay,
    var lxnsRomVersionThreshold: Int = 25500,
    // 配置分享锁：导入带锁的配置文件后置位，仅能通过正确口令或清除相关配置解除。
    var hideRivalConfig: Boolean = false,
    var noReshare: Boolean = false,
    // 解除口令的 SHA-256 哈希（空串 = 导出方未设口令，解除仅需确认）。
    var rivalUnlockCodeHash: String = "",
    var noReshareUnlockCodeHash: String = "",
    // 解除口令的加密字段（PBKDF2(口令hash)+AES-GCM 密文，双通道之一，以口令 hash 为准解密）。
    var rivalUnlockData: String = "",
    var noReshareUnlockData: String = "",
) {
    companion object {
        fun fromConfig(cfg: ConfigStorage): StoredConfig = StoredConfig(
            divingfishToken_enc = ConfigCrypto.encrypt(cfg.divingfishToken),
            lxnsToken_enc = ConfigCrypto.encrypt(cfg.lxnsToken),
            lxnsOAuthAccessToken_enc = ConfigCrypto.encrypt(cfg.lxnsOAuthAccessToken),
            lxnsOAuthRefreshToken_enc = ConfigCrypto.encrypt(cfg.lxnsOAuthRefreshToken),
            lxnsOAuthAccessTokenExpireAt = cfg.lxnsOAuthAccessTokenExpireAt,
            lxnsOAuthPkceVerifier_enc = ConfigCrypto.encrypt(cfg.lxnsOAuthPkceVerifier),
            divingfishOAuthAccessToken_enc = ConfigCrypto.encrypt(cfg.divingfishOAuthAccessToken),
            divingfishOAuthRefreshToken_enc = ConfigCrypto.encrypt(cfg.divingfishOAuthRefreshToken),
            divingfishOAuthAccessTokenExpireAt = cfg.divingfishOAuthAccessTokenExpireAt,
            divingfishOAuthPkceVerifier_enc = ConfigCrypto.encrypt(cfg.divingfishOAuthPkceVerifier),
            divingfishOAuthState = cfg.divingfishOAuthState,
            rivalSyncConfig_enc = if (cfg.rivalSyncConfig.hasSecrets()) {
                ConfigCrypto.encrypt(JSON.encodeToString(RivalSyncConfig.serializer(), cfg.rivalSyncConfig))
            } else {
                ""
            },
            syncConfig = cfg.syncConfig,
            localConfig = cfg.localConfig,
            userInfo = cfg.userInfo,
            scoreDisplayType = cfg.scoreDisplayType,
            scoreStyleType = cfg.scoreStyleType,
            lxnsRomVersionThreshold = cfg.lxnsRomVersionThreshold,
            hideRivalConfig = cfg.hideRivalConfig,
            noReshare = cfg.noReshare,
            rivalUnlockCodeHash = cfg.rivalUnlockCodeHash,
            noReshareUnlockCodeHash = cfg.noReshareUnlockCodeHash,
            rivalUnlockData = cfg.rivalUnlockData,
            noReshareUnlockData = cfg.noReshareUnlockData,
        )
    }

    fun toConfig(): ConfigStorage {
        val rival = if (rivalSyncConfig_enc.isNotEmpty()) {
            try {
                JSON.decodeFromString(RivalSyncConfig.serializer(), ConfigCrypto.decrypt(rivalSyncConfig_enc))
            } catch (e: Exception) {
                RivalSyncConfig()
            }
        } else {
            RivalSyncConfig()
        }
        return ConfigStorage(
            divingfishToken = ConfigCrypto.decrypt(divingfishToken_enc),
            lxnsToken = ConfigCrypto.decrypt(lxnsToken_enc),
            lxnsOAuthAccessToken = ConfigCrypto.decrypt(lxnsOAuthAccessToken_enc),
            lxnsOAuthRefreshToken = ConfigCrypto.decrypt(lxnsOAuthRefreshToken_enc),
            lxnsOAuthAccessTokenExpireAt = lxnsOAuthAccessTokenExpireAt,
            lxnsOAuthPkceVerifier = ConfigCrypto.decrypt(lxnsOAuthPkceVerifier_enc),
            divingfishOAuthAccessToken = ConfigCrypto.decrypt(divingfishOAuthAccessToken_enc),
            divingfishOAuthRefreshToken = ConfigCrypto.decrypt(divingfishOAuthRefreshToken_enc),
            divingfishOAuthAccessTokenExpireAt = divingfishOAuthAccessTokenExpireAt,
            divingfishOAuthPkceVerifier = ConfigCrypto.decrypt(divingfishOAuthPkceVerifier_enc),
            divingfishOAuthState = divingfishOAuthState,
            rivalSyncConfig = rival,
            syncConfig = syncConfig,
            localConfig = localConfig,
            userInfo = userInfo,
            scoreDisplayType = scoreDisplayType,
            scoreStyleType = scoreStyleType,
            lxnsRomVersionThreshold = lxnsRomVersionThreshold,
            hideRivalConfig = hideRivalConfig,
            noReshare = noReshare,
            rivalUnlockCodeHash = rivalUnlockCodeHash,
            noReshareUnlockCodeHash = noReshareUnlockCodeHash,
            rivalUnlockData = rivalUnlockData,
            noReshareUnlockData = noReshareUnlockData,
        )
    }
}

@Serializable
enum class ScoreDisplayType(val displayName: String) {
    Small("小"),
    Middle("中"),
    Large("大"),
}

@Serializable
enum class ScoreStyleType(val displayName: String) {
    ColorOverlay("颜色覆盖"),
    TextShadow("文本阴影"),
}

@Serializable
data class ConfigStorage(
    var divingfishToken: String = "",
    var lxnsToken: String = "",
    // 落雪 OAuth 令牌：OAuth 模式下优先使用，与 personal lxnsToken 并存。
    var lxnsOAuthAccessToken: String = "",
    var lxnsOAuthRefreshToken: String = "",
    var lxnsOAuthAccessTokenExpireAt: Long = 0, // epoch ms，access_token 过期时间
    var lxnsOAuthPkceVerifier: String = "", // PKCE code_verifier，getAuthorizeUrl 时生成，exchangeCodeForToken 后清空
    // 水鱼 OAuth 令牌：OAuth 模式下优先使用，与 Import-Token 并存。
    var divingfishOAuthAccessToken: String = "",
    var divingfishOAuthRefreshToken: String = "",
    var divingfishOAuthAccessTokenExpireAt: Long = 0, // epoch ms，access_token 过期时间
    var divingfishOAuthPkceVerifier: String = "", // PKCE code_verifier，getAuthorizeUrl 时生成，exchangeCodeForToken 后清空
    var divingfishOAuthState: String = "", // CSRF state，getAuthorizeUrl 时生成，回调校验后清空
    // 类型一（Rival 同步）配置：参考 Mizuki-plugin-Maimai-sync，全部留空由用户在设置页自填，
    // 不内置任何机台/鉴权/加密敏感信息。
    var rivalSyncConfig: RivalSyncConfig = RivalSyncConfig(),
    var syncConfig: SyncConfig = SyncConfig(),
    var localConfig: LocalConfig = LocalConfig(),
    var userInfo: UserInfo = UserInfo(),
    var scoreDisplayType: ScoreDisplayType = ScoreDisplayType.Small,
    var scoreStyleType: ScoreStyleType = ScoreStyleType.ColorOverlay,
    var lxnsRomVersionThreshold: Int = 25500,
    // 配置分享锁：导入带锁的配置文件后置位，仅能通过正确口令或清除相关配置解除。
    var hideRivalConfig: Boolean = false,
    var noReshare: Boolean = false,
    // 解除口令的 SHA-256 哈希（空串 = 导出方未设口令，解除仅需确认）。
    var rivalUnlockCodeHash: String = "",
    var noReshareUnlockCodeHash: String = "",
    // 解除口令的加密字段（PBKDF2(口令hash)+AES-GCM 密文，双通道之一，以口令 hash 为准解密）。
    var rivalUnlockData: String = "",
    var noReshareUnlockData: String = "",
)

/**
 * 类型一（Rival 同步）配置：对应 Mizuki 插件的 keychip + 游戏服务器 + 加密参数。
 * 全部字段留空，由用户在设置页"Rival 设置"二级菜单自填，不内置。
 *
 * 字段说明（参考 Mizuki-plugin-Maimai-sync/plugins/maimai_sync）：
 *  - keychip：机台号（Mizuki keychip.csv 的 Keychip 列，作为 User-Agent 标识）
 *  - gameServerUrl：游戏服务器基础网址，含尾斜杠（如 "https://<game-server-url>"）
 *  - apiHash：api 端点哈希值，由 md5("GetUserRivalMusicApiMaimaiChn" + cryptObfuscate) 算出；
 *    和 gameServerUrl 分开填，拼接成完整请求 URL。
 *  - authServerUrl：Auth 服务器的鉴权节点，**直接拿来用不拼接**（如 "http://ai.sys-allnet.cn/wc_aime/api/get_data"）。
 *    **不强制**：仅在使用 QRcode 鉴权时要求填入；如果用户直接输入 userId 则不需要此字段。
 *  - cryptKey / cryptIv：AES-CBC 加密的 key/iv（Mizuki CRYPT_VERSIONS[ver].key/iv）
 *  - cryptEncoding：编码版本（Mizuki CRYPT_VERSIONS[ver].encoding，作 Mai-Encoding header）
 *  - cryptObfuscate：混淆参数（Mizuki CRYPT_VERSIONS[ver].obfuscate，作 User-Agent 拼接）
 *  - authSalt：鉴权签名盐（Mizuki config.py AUTH_SALT）
 *  - userId / token：QR 鉴权后本地保存。userId 在设置页可编辑、星号隐私显示；
 *    token 一般短期失效，留作缓存；过期重新 QR 鉴权刷新即可。
 */
@Serializable
data class RivalSyncConfig(
    var keychip: String = "",
    var gameServerUrl: String = "",
    var apiHash: String = "",
    var authServerUrl: String = "",
    var cryptKey: String = "",
    var cryptIv: String = "",
    var cryptEncoding: String = "",
    var cryptObfuscate: String = "",
    var authSalt: String = "",
    // QR 鉴权后本地保存的 userId/token。userId 在设置页可编辑、星号隐私显示。
    // token 一般短期失效，留作缓存；过期重新 QR 鉴权刷新即可。
    var userId: String = "",
    var token: String = "",
) {
    /** 是否含有需要加密的敏感字段（任一非空即视为有）。 */
    fun hasSecrets(): Boolean =
        keychip.isNotEmpty() || gameServerUrl.isNotEmpty() || apiHash.isNotEmpty() ||
            authServerUrl.isNotEmpty() || cryptKey.isNotEmpty() || cryptIv.isNotEmpty() ||
            cryptEncoding.isNotEmpty() || cryptObfuscate.isNotEmpty() ||
            authSalt.isNotEmpty() || userId.isNotEmpty() || token.isNotEmpty()
}

@Serializable
data class SyncConfig(
    var maimaiIncrementalFetchScore: Boolean = true,
    var maimaiSyncDifficulty: List<Int> = MaimaiEnums.Difficulty.entries.map { it.diffIndex },
    var chuniSyncDifficulty: List<Int> = ChuniEnums.Difficulty.entries.map { it.diffIndex }
)

@Serializable
data class LocalConfig(
    var checkUpdate: Boolean = true,
    var checkSnapshotUpdate: Boolean = false,
    var cacheScore: Boolean = false,
    var parseMaimaiUserInfo: Boolean = false,
    var currentMaimaiVersion: Int = 0
)

@Serializable
data class UserInfo(
    var name: String = "Maiupload",
    var maimaiDan: Int = 0,
    var maimaiIcon: Int = 1,
    var maimaiPlate: Int = 1,
    var maimaiClass: Int = 0,
    val chuniCharacter: Int = 0,
    var shougou: String = "Generated by Maiupload",
    var shougouColor: String = "normal",
)
