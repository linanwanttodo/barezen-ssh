// shared/src/commonTest/kotlin/com/barezen/barezen_ssh/settings/AppSettingsTest.kt
package com.barezen.barezen_ssh.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSettingsTest {

    /** 与 FileSettingsRepository 必须用同一套配置，否则"能存不能读"。 */
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test fun defaultsAreStable() {
        val d = AppSettings.Default
        assertEquals(AppSettings.SCHEMA_VERSION, d.schemaVersion)
        assertEquals(Theme.FOLLOW_SYSTEM, d.theme)
        assertEquals("Noto Sans SC", d.uiFont)
        assertEquals(1.0f, d.uiScale)
        assertEquals("zh-CN", d.language)
        assertEquals("JetBrains Mono", d.terminalFont)
        assertEquals("graphite", d.terminalPalette)
        assertEquals(false, d.copyOnSelect)
        assertEquals(true, d.shiftInsertPaste)
        assertEquals(false, d.sudoAutofill)
        assertNull(d.autoConnectServerId)
        assertEquals(false, d.hideAddresses)
        assertEquals(ConflictPolicy.ASK, d.conflictPolicy)
        assertNull(d.updateRepo)
        assertEquals(UpdateChannel.STABLE, d.updateChannel)
        assertEquals(false, d.autoCheckUpdates)
        assertNull(d.feedbackUrl)
    }

    @Test fun roundTripSerialization() {
        val original = AppSettings.Default.copy(
            theme = Theme.DARK,
            uiScale = 1.25f,
            updateRepo = "acme/barezen-ssh",
            feedbackUrl = "https://example.com/issues",
            autoConnectServerId = "srv-1",
        )
        val decoded = json.decodeFromString<AppSettings>(json.encodeToString(original))
        assertEquals(original, decoded)
    }

    @Test fun unknownKeysAreIgnored() {
        val text = """{"schemaVersion":1,"theme":"DARK","futureField":"whatever"}"""
        val decoded = json.decodeFromString<AppSettings>(text)
        assertEquals(Theme.DARK, decoded.theme)
    }

    @Test fun missingKeysUseDefaults() {
        val decoded = json.decodeFromString<AppSettings>("""{"theme":"DARK"}""")
        assertEquals(Theme.DARK, decoded.theme)
        assertEquals(AppSettings.Default.uiScale, decoded.uiScale)
        assertEquals(AppSettings.Default.conflictPolicy, decoded.conflictPolicy)
    }

    @Test fun unknownEnumValueCoercesToDefault() {
        // coerceInputValues=true 时未知枚举值 → 该属性的默认值，而不是抛异常
        val decoded = json.decodeFromString<AppSettings>("""{"theme":"NEON","updateChannel":"NIGHTLY"}""")
        assertEquals(Theme.FOLLOW_SYSTEM, decoded.theme)
        assertEquals(UpdateChannel.STABLE, decoded.updateChannel)
    }

    @Test fun sanitizeClampsOutOfRangeScale() {
        val r = AppSettings.Default.copy(uiScale = 99f).sanitized()
        assertEquals(1.5f, r.value.uiScale)
        assertTrue(r.warnings.isNotEmpty(), "收敛必须留下警告")
    }

    @Test fun sanitizeClampsNegativeScale() {
        val r = AppSettings.Default.copy(uiScale = -1f).sanitized()
        assertEquals(1.0f, r.value.uiScale)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun sanitizeFallsBackOnUnknownFonts() {
        val r = AppSettings.Default.copy(uiFont = "Comic Sans", terminalFont = "Papyrus").sanitized()
        assertEquals(AppSettings.UI_FONT_DEFAULT, r.value.uiFont)
        assertEquals(AppSettings.TERMINAL_FONT_DEFAULT, r.value.terminalFont)
        assertEquals(2, r.warnings.size)
    }

    @Test fun sanitizeFallsBackOnUnknownPaletteAndLanguage() {
        val r = AppSettings.Default.copy(terminalPalette = "solarized", language = "en-US").sanitized()
        assertEquals(AppSettings.TERMINAL_PALETTE_DEFAULT, r.value.terminalPalette)
        assertEquals(AppSettings.LANGUAGE_DEFAULT, r.value.language)
        assertEquals(2, r.warnings.size)
    }

    @Test fun sanitizeRejectsMalformedRepo() {
        val r = AppSettings.Default.copy(updateRepo = "not a repo").sanitized()
        assertNull(r.value.updateRepo)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun sanitizeAcceptsValidRepo() {
        val r = AppSettings.Default.copy(updateRepo = "acme/barezen-ssh").sanitized()
        assertEquals("acme/barezen-ssh", r.value.updateRepo)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun sanitizeRejectsNonHttpFeedbackUrl() {
        val r = AppSettings.Default.copy(feedbackUrl = "ftp://example.com").sanitized()
        assertNull(r.value.feedbackUrl)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun sanitizeBlanksAutoConnectId() {
        val r = AppSettings.Default.copy(autoConnectServerId = "   ").sanitized()
        assertNull(r.value.autoConnectServerId)
    }

    @Test fun sanitizeIsIdempotent() {
        val once = AppSettings.Default.copy(uiScale = 99f, uiFont = "nope").sanitized()
        val twice = once.value.sanitized()
        assertEquals(once.value, twice.value)
        assertTrue(twice.warnings.isEmpty(), "已合法的设置再收敛不应再产生警告")
    }
}
