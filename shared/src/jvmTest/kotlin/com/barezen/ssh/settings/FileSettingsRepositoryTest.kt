// shared/src/jvmTest/kotlin/com/barezen/ssh/settings/FileSettingsRepositoryTest.kt
package com.barezen.ssh.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileSettingsRepositoryTest {

    private fun tmpFile(): File = File.createTempFile("settings", ".json").apply { delete() }

    @Test fun missingFileYieldsDefaultsAndDoesNotCreate() {
        val f = tmpFile()
        val load = FileSettingsRepository(f).load()
        val ok = assertIs<SettingsLoad.Ok>(load)
        assertEquals(AppSettings.Default, ok.settings)
        assertTrue(ok.warnings.isEmpty())
        assertFalse(f.exists(), "只读加载不应创建文件")
    }

    @Test fun roundTripPersistsAllFields() {
        val f = tmpFile()
        val repo = FileSettingsRepository(f)
        val s = AppSettings.Default.copy(
            theme = Theme.DARK,
            uiScale = 1.5f,
            conflictPolicy = ConflictPolicy.RENAME,
            updateRepo = "acme/barezen-ssh",
            autoCheckUpdates = true,
            feedbackUrl = "https://example.com/f",
            autoConnectServerId = "srv-9",
        )
        repo.save(s)
        val ok = assertIs<SettingsLoad.Ok>(repo.load())
        assertEquals(s, ok.settings)
    }

    @Test fun corruptFileIsQuarantinedAndDefaultsUsed() {
        val f = tmpFile().apply { writeText("{ not json") }
        val original = f.readText()

        val load = FileSettingsRepository(f).load()

        val rec = assertIs<SettingsLoad.Recovered>(load)
        assertEquals(AppSettings.Default, rec.settings)
        val qp = assertNotNull(rec.quarantinePath, "必须报告隔离副本路径")
        val q = File(qp)
        assertTrue(q.isFile, "隔离副本必须真的落地")
        assertEquals(original, q.readText(), "隔离副本必须原样保留原文件内容")
        assertFalse(f.exists(), "原路径应已被让出（已改名）")
    }

    @Test fun saveDoesNotDestroyUnreadableFile() {
        val f = tmpFile().apply { writeText("{ not json") }
        val original = f.readText()
        val repo = FileSettingsRepository(f)

        repo.save(AppSettings.Default.copy(uiScale = 1.25f))

        // 新值写进去了……
        assertEquals(1.25f, assertIs<SettingsLoad.Ok>(repo.load()).settings.uiScale)
        // ……且原坏内容仍能从隔离副本找回
        val preserved = f.parentFile!!.listFiles().orEmpty()
            .any { it.name.startsWith("${f.name}.corrupt-") && it.readText() == original }
        assertTrue(preserved, "不可读的 settings.json 被覆盖销毁；应先隔离再写")
    }

    @Test fun writeIsAtomicAndLeavesNoTempResidue() {
        val f = tmpFile()
        FileSettingsRepository(f).save(AppSettings.Default.copy(theme = Theme.DARK))
        assertTrue(
            f.parentFile!!.listFiles().orEmpty().none { it.name.startsWith("${f.name}.tmp") },
            "写入后残留了 .tmp",
        )
    }

    @Test fun partialJsonUsesDefaults() {
        val f = tmpFile().apply { writeText("""{"theme":"DARK"}""") }
        val ok = assertIs<SettingsLoad.Ok>(FileSettingsRepository(f).load())
        assertEquals(Theme.DARK, ok.settings.theme)
        assertEquals(AppSettings.Default.uiScale, ok.settings.uiScale)
    }

    @Test fun unknownFieldsAreIgnored() {
        val f = tmpFile().apply { writeText("""{"theme":"DARK","futureField":123}""") }
        val ok = assertIs<SettingsLoad.Ok>(FileSettingsRepository(f).load())
        assertEquals(Theme.DARK, ok.settings.theme)
    }

    @Test fun invalidValuesAreClampedOnLoadWithWarnings() {
        val f = tmpFile().apply {
            writeText("""{"uiScale":99.0,"uiFont":"Comic Sans"}""")
        }
        val ok = assertIs<SettingsLoad.Ok>(FileSettingsRepository(f).load())
        assertEquals(1.5f, ok.settings.uiScale)
        assertEquals(AppSettings.UI_FONT_DEFAULT, ok.settings.uiFont)
        assertEquals(2, ok.warnings.size, "两处收敛应产生两条警告")
    }

    @Test fun unknownEnumValueInFileDoesNotThrow() {
        val f = tmpFile().apply { writeText("""{"theme":"NEON"}""") }
        val ok = assertIs<SettingsLoad.Ok>(FileSettingsRepository(f).load())
        assertEquals(Theme.FOLLOW_SYSTEM, ok.settings.theme)
    }
}
