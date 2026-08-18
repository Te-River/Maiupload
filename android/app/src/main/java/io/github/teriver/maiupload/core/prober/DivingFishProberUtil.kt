package io.github.teriver.maiupload.core.prober

import android.util.Log
import io.github.teriver.maiupload.Application.Companion.application
import io.github.teriver.maiupload.GlobalViewModel
import io.github.teriver.maiupload.core.data.chuni.ChuniData
import io.github.teriver.maiupload.core.data.chuni.ChuniEnums
import io.github.teriver.maiupload.core.data.chuni.ChuniScoreManager.writeChuniScoreCache
import io.github.teriver.maiupload.core.data.maimai.MaimaiData
import io.github.teriver.maiupload.core.data.maimai.MaimaiEnums
import io.github.teriver.maiupload.core.data.maimai.MaimaiScoreManager.writeMaimaiScoreCache
import io.github.teriver.maiupload.core.database.entity.ChuniScoreEntity
import io.github.teriver.maiupload.core.database.entity.MaimaiScoreEntity
import io.github.teriver.maiupload.core.prober.models.divingfish.DivingFishGetChuniSCoreResponse
import io.github.teriver.maiupload.core.prober.models.divingfish.DivingFishGetMaimaiScoresResponse
import io.github.teriver.maiupload.core.prober.models.divingfish.DivingFishMaimaiScoreBody
import io.github.teriver.maiupload.core.prober.models.divingfish.DivingFishPlayerProfile
import io.github.teriver.maiupload.core.prober.divingfish.DivingFishOAuthUtil
import io.github.teriver.maiupload.core.prober.divingfish.DivingFishOAuthUserInfo
import io.github.teriver.maiupload.core.utils.ParseScorePageUtil
import io.github.teriver.maiupload.core.utils.DebugLog
import io.github.teriver.maiupload.core.utils.ErrorLog
import io.github.teriver.maiupload.ui.compose.sync.SyncViewModel
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

class DivingFishProberUtil : IProberUtil {
    private val baseApiUrl = "https://www.diving-fish.com/api"

    /**
     * 按当前 Token 输入模式解析请求头：OAuth 模式（divingfishTokenInputMode==1）走
     * `Authorization: Bearer <access_token>`（必要时刷新），Token 模式沿用
     * `Import-Token: <importToken>`。返回 null 表示 OAuth 失效需重新授权。
     */
    private suspend fun resolveAuthHeader(importToken: String, forceRefresh: Boolean = false): Pair<String, String>? {
        return if (SyncViewModel.divingfishTokenInputMode == 1) {
            val accessToken = DivingFishOAuthUtil.ensureValidAccessToken(force = forceRefresh)
                ?: return null
            "Authorization" to "Bearer $accessToken"
        } else {
            "Import-Token" to importToken
        }
    }

    override suspend fun updateUserInfo(importToken: String) {
        val auth = resolveAuthHeader(importToken) ?: run {
            sendMessageToUi("水鱼授权已失效，请重新授权")
            return
        }
        // OAuth 模式：/player/profile 端点不支持 Bearer，改用 OIDC userinfo 端点取用户名
        if (SyncViewModel.divingfishTokenInputMode == 1) {
            val resp = client.get("https://auth.diving-fish.com/oauth/userinfo") {
                header(auth.first, auth.second)
            }
            if (resp.status.value == 200) {
                val info = resp.body<DivingFishOAuthUserInfo>()
                application.configManager.config.userInfo.name =
                    info.preferred_username.ifEmpty { info.name.ifEmpty { info.nickname } }
                application.configManager.save()
            } else {
                sendMessageToUi("同步用户信息失败: ${resp.bodyAsText()}")
            }
            return
        }
        val resp = client.get("https://www.diving-fish.com/api/maimaidxprober/player/profile") {
            header(auth.first, auth.second)
        }
        val data = resp.body<DivingFishPlayerProfile>()
        application.configManager.config.userInfo.name = data.username
        application.configManager.config.userInfo.maimaiDan = data.additionalRating
        application.configManager.save()
    }

    override suspend fun uploadMaimaiProberData(
        importToken: String,
        authUrl: String,
        externalScores: List<MaimaiScoreEntity>?
    ): Boolean {
        val isCache = application.configManager.config.localConfig.cacheScore

        application.sendNotification("水鱼查分器", "正在进行查分")
        sendMessageToUi("开始上传至水鱼查分器")

        // externalScores 非空（如 Rival 同步拉的对手成绩）：跳过 VPN 抓包 pageparser，
        // 直接转 DivingFishMaimaiScoreBody POST 给水鱼 update_records。
        // 对齐 Mizuki lib_fish._transform_for_fish + upload_to_fish。
        if (externalScores != null) {
            if (externalScores.isEmpty()) {
                sendMessageToUi("通过 Rival 同步失败：未拉到成绩")
                return false
            }
            val payload = externalScores.map {
                // 对齐 Mizuki lib_fish._transform_for_fish：type 按 musicId 大小判定，
                // DX 谱面（10000<=mid<100000）type=DX 且 song_id 取余；宴会场（>=100000）
                // 水鱼无 utage 归为 SD；标准谱面原样
                val fishType = if (it.songId in 10000 until 100000) "DX" else "SD"
                val fishSongId = if (it.songId in 10000 until 100000) it.songId % 10000 else it.songId
                DivingFishMaimaiScoreBody(
                    songId = fishSongId,
                    title = it.title,
                    level = it.level.toString(),
                    levelIndex = it.diff.diffIndex,
                    type = it.type.type2,  // "SD"/"DX"/"UTAGE"
                    achievements = it.achievement / 10000.0f,  // 对齐 Mizuki /10000
                    dxScore = it.dxScore,
                    rate = it.rankType.rank,
                    fc = it.fullComboType.typeName,
                    fs = it.syncType.syncName,
                    levelLabel = "",
                    ra = it.rating
                )
            }
            val bodyStr = Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(DivingFishMaimaiScoreBody.serializer()),
                payload
            )
            var ok = false
            try {
                val auth = resolveAuthHeader(importToken) ?: run {
                    sendMessageToUi("水鱼授权已失效，请重新授权")
                    return false
                }
                val postResult = client.post("$baseApiUrl/maimaidxprober/player/update_records") {
                    headers {
                        append(auth.first, auth.second)
                        append(HttpHeaders.ContentType, "application/json")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(bodyStr)
                }
                ok = postResult.status.value in 200..299
                DebugLog.log("I", "DivingFishProberUtil", "通过 Rival 同步已上传 ${externalScores.size} 条成绩到水鱼查分器, 接口信息: ${postResult.bodyAsText()}")
                sendMessageToUi("通过 Rival 同步已上传 ${externalScores.size} 条成绩到水鱼查分器")
            } catch (e: Exception) {
                ErrorLog.logError("DivingFishProberUtil", "通过 Rival 同步上传水鱼失败", e)
                sendMessageToUi("通过 Rival 同步上传水鱼失败: ${e.message}")
            }
            if (isCache) {
                writeMaimaiScoreCache(externalScores)
            }
            GlobalViewModel.maimaiHooking = false
            application.sendNotification("水鱼查分器", "查分完毕")
            return ok
        }

        val scores = mutableListOf<MaimaiScoreEntity>()
        var allOk = true
        fetchMaimaiScorePage(authUrl) { diff, body ->
            DebugLog.log("I", "DivingFishProberUtil", "正在上传${diff.diffName}成绩到水鱼查分器")
            try {
                val auth = resolveAuthHeader(importToken) ?: run {
                    sendMessageToUi("水鱼授权已失效，请重新授权")
                    return@fetchMaimaiScorePage
                }
                val result = client.post("$baseApiUrl/pageparser/page") {
                    headers {
                        append(HttpHeaders.ContentType, "text/plain")
                    }
                    contentType(ContentType.Text.Plain)
                    setBody(body)
                }

                if (isCache) {
                    val scoreBody = result.body<List<DivingFishMaimaiScoreBody>>()
                    scoreBody.forEach { score ->
                        val res = MaimaiData.MAIMAI_SONG_LIST.find { it.title == score.title }
                        if (res != null) {
                            var version =0
                            var songType = MaimaiEnums.SongType.STANDARD
                            var level = 0F
                            if (score.type == "DX") {
                                res.difficulties.dx[score.levelIndex].version
                                songType = MaimaiEnums.SongType.DX
                                level = res.difficulties.dx[score.levelIndex].levelValue
                            } else {
                                res.difficulties.standard[score.levelIndex].version
                                level = res.difficulties.standard[score.levelIndex].levelValue
                            }
                            scores.add(MaimaiScoreEntity(
                                songId = res.id,
                                title = score.title,
                                level = score.ds,
                                achievement = score.achievements,
                                dxScore = score.dxScore,
                                rating = score.ra,
                                version = version,
                                type = songType,
                                diff = MaimaiEnums.Difficulty.getDifficultyWithIndex(score.levelIndex),
                                rankType = MaimaiEnums.RankType.getRankTypeByScore(score.achievements),
                                syncType = MaimaiEnums.SyncType.getSyncTypeByName(score.fs),
                                fullComboType = MaimaiEnums.FullComboType.getFullComboTypeByName(score.fc),
                            ))
                        }
                    }
                }

                val postResult = client.post("$baseApiUrl/maimaidxprober/player/update_records") {
                    headers {
                        append(auth.first, auth.second)
                        append(HttpHeaders.ContentType, "application/json")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(result.bodyAsText())
                }
                DebugLog.log("I", "DivingFishProberUtil", "已上传${diff.diffName}成绩到水鱼查分器, 接口信息: ${postResult.bodyAsText()}")
            } catch (e: Exception) {
                ErrorLog.logError("DivingFishProberUtil", "上传${diff.diffName}成绩到水鱼查分器失败", e)
            }
        }
        sendMessageToUi("上传舞萌DX成绩到水鱼查分器完成")
        DebugLog.log("D", "DivingFishProberUtil", "上传完毕")
        GlobalViewModel.maimaiHooking = false
        application.sendNotification("水鱼查分器", "查分完毕")
        if (isCache) {
            writeMaimaiScoreCache(scores)
        }
        return true
    }

    override suspend fun uploadChunithmProberData(
        importToken: String,
        authUrl: String
    ) {
        val isCache = application.configManager.config.localConfig.cacheScore
        val scores = mutableListOf<ChuniScoreEntity>()

        application.sendNotification("水鱼查分器", "正在进行查分")
        sendMessageToUi("开始上传至水鱼查分器")
        fetchChuniScores(authUrl) { diff, body ->
            DebugLog.log("I", "DivingFishProberUtil", "正在上传${diff.diffName}成绩到水鱼查分器")
            val recentParam = if (diff.diffName.lowercase().contains("recent")) "?recent=1" else ""
            try {
                val auth = resolveAuthHeader(importToken) ?: run {
                    sendMessageToUi("水鱼授权已失效，请重新授权")
                    return@fetchChuniScores
                }
                client.post("$baseApiUrl/chunithmprober/player/update_records_html$recentParam") {
                    headers {
                        append(auth.first, auth.second)
                        append(HttpHeaders.ContentType, "text/plain")
                    }
                    contentType(ContentType.Text.Plain)
                    setBody(body)
                }

                if (isCache) {
                    scores.addAll(ParseScorePageUtil.parseChuni(body, diff))
                }

                DebugLog.log("I", "DivingFishProberUtil", "已上传${diff.diffName}成绩到水鱼查分器")
            } catch (e: Exception) {
                ErrorLog.logError("DivingFishProberUtil", "上传${diff.diffName}成绩到水鱼查分器失败", e)
            }
        }
        sendMessageToUi("上传中二节奏成绩到水鱼查分器完成")
        DebugLog.log("D", "DivingFishProberUtil", "上传完毕")
        GlobalViewModel.chuniHooking = false
        application.sendNotification("水鱼查分器", "查分完毕")
        if (isCache) {
            writeChuniScoreCache(scores)
        }
    }

    override suspend fun getMaimaiProberData(importToken: String): List<MaimaiScoreEntity> {
        try {
            val auth = resolveAuthHeader(importToken) ?: run {
                sendMessageToUi("水鱼授权已失效，请重新授权")
                return emptyList()
            }
            val result = client.get("$baseApiUrl/maimaidxprober/player/records") {
                headers {
                    append(auth.first, auth.second)
                }
            }
            val body = result.body<DivingFishGetMaimaiScoresResponse>()
            val scores = mutableListOf<MaimaiScoreEntity>()
            body.records.forEach {
                val type = MaimaiEnums.SongType.getSongTypeByName(it.type)
                val diff = MaimaiEnums.Difficulty.getDifficultyWithIndex(it.levelIndex)
                val levelValue = MaimaiData.getLevelValue(it.title, diff, type)
                val version = MaimaiData.getChartVersion(it.title, diff, type)
                scores.add(
                    MaimaiScoreEntity(
                        songId = MaimaiData.getSongIdFromTitle(it.title),
                        title = it.title,
                        level = levelValue,
                        achievement = it.achievements,
                        dxScore = it.dxScore,
                        rating = it.ra,
                        version = version,
                        type = type,
                        diff = diff,
                        rankType = MaimaiEnums.RankType.getRankTypeByScore(it.achievements),
                        syncType = MaimaiEnums.SyncType.getSyncTypeByName(it.fs),
                        fullComboType = MaimaiEnums.FullComboType.getFullComboTypeByName(it.fc)
                    )
                )
            }
            return scores
        } catch (e: Exception) {
            ErrorLog.logError("DivingFishProberUtil", "获取舞萌DX成绩失败", e)
            sendMessageToUi("获取舞萌DX成绩失败")
            return emptyList()
        }
    }

    override suspend fun getChuniProberData(importToken: String): List<ChuniScoreEntity> {
        try {
            val auth = resolveAuthHeader(importToken) ?: run {
                sendMessageToUi("水鱼授权已失效，请重新授权")
                return emptyList()
            }
            val result = client.get("$baseApiUrl/chunithmprober/player/records") {
                headers {
                    append(auth.first, auth.second)
                }
            }
            val body = result.body<DivingFishGetChuniSCoreResponse>()
            val scores = arrayListOf<ChuniScoreEntity>()
            body.records.best.forEach {
                val diff = ChuniEnums.Difficulty.getDifficultyWithIndex(it.levelIndex)
                val version = ChuniData.getChartVersion(it.title, diff)
                scores.add(
                    ChuniScoreEntity(
                        songId = ChuniData.getSongIdFromTitle(it.title),
                        title = it.title,
                        level = it.ds,
                        score = it.score,
                        rating = it.ra,
                        version = version,
                        diff = diff,
                        rankType = ChuniEnums.RankType.getRankTypeByScore(it.score),
                        fullComboType = ChuniEnums.FullComboType.NULL,
                        fullChainType = ChuniEnums.FullChainType.NULL,
                        clearType = ChuniEnums.ClearType.FAILED
                    )
                )
            }
            return scores
        } catch (e: Exception) {
            ErrorLog.logError("DivingFishProberUtil", "获取中二节奏成绩失败", e)
            sendMessageToUi("获取中二节奏成绩失败")
            return emptyList()
        }
    }
}