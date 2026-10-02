// shared/src/jvmTest/kotlin/com/barezen/ssh/settings/AiSettingsCompatTest.kt
package com.barezen.ssh.settings

import com.barezen.ssh.app.AiConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T-7：智能助手设置字段的向后兼容与收敛规则。 */
class AiSettingsCompatTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun oldJsonWithoutAiFieldsDecodesWithDefaults() {
        // v0.9 形态的 settings.json：没有 aiEndpoint/aiModel 字段，必须照常解码
        val old = """
            {
              "schemaVersion": 1,
              "theme": "DARK",
              "terminalPalette": "graphite"
            }
        """.trimIndent()
        val s = json.decodeFromString<AppSettings>(old)
        assertEquals("", s.aiEndpoint)
        assertEquals("", s.aiModel)
    }

    @Test
    fun aiFieldsRoundTripThroughJson() {
        val s = AppSettings.Default.copy(aiEndpoint = "https://api.example.com/v1", aiModel = "gpt-4o-mini")
        val encoded = json.encodeToString(AppSettings.serializer(), s)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals("https://api.example.com/v1", decoded.aiEndpoint)
        assertEquals("gpt-4o-mini", decoded.aiModel)
    }

    @Test
    fun sanitizedTrimsTrailingSlashAndSpaces() {
        val r = AppSettings.Default
            .copy(aiEndpoint = "  https://api.example.com/v1/  ", aiModel = " gpt-4o-mini ")
            .sanitized()
        assertEquals("https://api.example.com/v1", r.value.aiEndpoint)
        assertEquals("gpt-4o-mini", r.value.aiModel)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun sanitizedClearsNonHttpEndpointWithWarning() {
        val r = AppSettings.Default.copy(aiEndpoint = "ftp://example.com/v1").sanitized()
        assertEquals("", r.value.aiEndpoint)
        assertEquals(1, r.warnings.size)
        assertContains(r.warnings[0], "智能助手")
    }

    @Test
    fun sanitizedKeepsBlankEndpointSilent() {
        val r = AppSettings.Default.copy(aiEndpoint = "").sanitized()
        assertEquals("", r.value.aiEndpoint)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun chatUrlAppendsPath() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AiConfig("https://api.example.com/v1", "m", "k").chatUrl,
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AiConfig("https://api.example.com/v1/", "m", "k").chatUrl,
        )
    }
}
