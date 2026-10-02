// shared/src/jvmMain/kotlin/com/barezen/ssh/settings/UpdateChecker.kt
package com.barezen.ssh.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

sealed interface UpdateResult {
    data object NotConfigured : UpdateResult
    data class UpToDate(val latest: String, val checkedAt: String) : UpdateResult
    data class NewerAvailable(val latest: String, val url: String, val checkedAt: String) : UpdateResult

    /** 非 semver tag：不谎报「有新版本」，如实说无法比较（设计 §11.2）。 */
    data class Uncomparable(val latest: String, val checkedAt: String) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/**
 * GitHub Releases 更新检查（设计 §11）。
 *
 * @param getJson 注入式 fetch：生产用 JDK HttpClient（[production]），测试注入假实现**零网络**。
 * 未配置更新源（repo 空白）→ [UpdateResult.NotConfigured]，**不发起任何请求**。
 */
class UpdateChecker(private val getJson: suspend (String) -> String) {

    suspend fun check(repo: String, channel: UpdateChannel, currentVersion: String): UpdateResult {
        if (repo.isBlank()) return UpdateResult.NotConfigured
        val now = Instant.now().toString()
        val text = try {
            getJson("https://api.github.com/repos/$repo/releases")
        } catch (e: Exception) {
            return UpdateResult.Failed(mapNetworkError(e))
        }
        val releases = try {
            json.parseToJsonElement(text).jsonArray
        } catch (_: Exception) {
            return UpdateResult.Failed("检查失败：响应格式异常")
        }
        val picked = releases.asSequence()
            .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .filter { (it["draft"]?.jsonPrimitive?.content ?: "false") != "true" }
            .filter { channel == UpdateChannel.PREVIEW ||
                (it["prerelease"]?.jsonPrimitive?.content ?: "false") != "true" }
            .firstOrNull() ?: return UpdateResult.UpToDate(currentVersion, now)

        val latest = picked["tag_name"]?.jsonPrimitive?.content.orEmpty().removePrefix("v")
        val url = picked["html_url"]?.jsonPrimitive?.content.orEmpty()
        return when (compare(latest, currentVersion)) {
            CompareResult.NEWER -> UpdateResult.NewerAvailable(latest, url, now)
            CompareResult.UNCOMPARABLE -> UpdateResult.Uncomparable(latest, now)
            CompareResult.NOT_NEWER -> UpdateResult.UpToDate(latest, now)
        }
    }

    private enum class CompareResult { NEWER, NOT_NEWER, UNCOMPARABLE }

    /** 去 `v` 前缀后按 `.` 切段做数字比较；任一段非数字 → UNCOMPARABLE（不谎报）。 */
    private fun compare(latestTag: String, current: String): CompareResult {
        val latest = latestTag.removePrefix("v")
        if (latest == current) return CompareResult.NOT_NEWER
        val a = latest.split('.')
        val b = current.split('.')
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a.getOrNull(i) ?: "0"
            val bi = b.getOrNull(i) ?: "0"
            val an = ai.toIntOrNull() ?: return CompareResult.UNCOMPARABLE
            val bn = bi.toIntOrNull() ?: return CompareResult.UNCOMPARABLE
            if (an != bn) return if (an > bn) CompareResult.NEWER else CompareResult.NOT_NEWER
        }
        return CompareResult.NOT_NEWER
    }

    /** 错误映射（设计 §11.4）：404 / 403 / 网络 / 其他，给明确原因，不自动纠正。 */
    private fun mapNetworkError(e: Exception): String {
        val msg = e.message ?: e.toString()
        return when {
            e is IOException && msg.contains("404") -> "检查失败：仓库不存在或没有 Release"
            e is IOException && msg.contains("403") -> "检查失败：GitHub 限流，请稍后再试"
            e is IOException -> "检查失败：网络不可达"
            else -> "检查失败：$msg"
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 生产实现：JDK 自带 HttpClient（零新依赖）。GitHub 要求 UA，缺了会 403。 */
        fun production(): UpdateChecker {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
            return UpdateChecker { url ->
                val request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "BareZen-SSH")
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build()
                client.send(request, HttpResponse.BodyHandlers.ofString()).body()
            }
        }
    }
}
