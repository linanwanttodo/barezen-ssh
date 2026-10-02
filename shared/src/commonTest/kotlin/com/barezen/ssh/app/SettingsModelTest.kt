// shared/src/commonTest/kotlin/com/barezen/ssh/app/SettingsModelTest.kt
package com.barezen.ssh.app

import com.barezen.ssh.settings.AppSettings
import com.barezen.ssh.settings.SettingsLoad
import com.barezen.ssh.settings.SettingsRepository
import com.barezen.ssh.settings.SettingsWriteException
import com.barezen.ssh.settings.Theme
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 内存假仓库：记录 save 调用次数，可设成抛异常。 */
private class FakeRepo(
    var toLoad: SettingsLoad = SettingsLoad.Ok(AppSettings.Default, emptyList()),
    var failSave: Boolean = false,
) : SettingsRepository {
    var saveCount = 0
    var lastSaved: AppSettings? = null
    override fun load(): SettingsLoad = toLoad
    override fun save(settings: AppSettings) {
        saveCount++
        if (failSave) throw SettingsWriteException("disk full")
        lastSaved = settings
    }
}

class SettingsModelTest {

    @Test fun loadPopulatesFromRepository() {
        val repo = FakeRepo(SettingsLoad.Ok(AppSettings.Default.copy(theme = Theme.DARK), emptyList()))
        val m = SettingsModel(repo)
        m.load()
        assertEquals(Theme.DARK, m.settings.theme)
        assertNull(m.loadNotice)
    }

    @Test fun updateAppliesAndPersistsImmediately() {
        val repo = FakeRepo()
        val m = SettingsModel(repo)
        m.load()
        m.update { it.copy(uiScale = 1.25f) }
        assertEquals(1.25f, m.settings.uiScale, "内存态应立即变化")
        assertEquals(1, repo.saveCount, "应立刻落盘一次")
        assertEquals(1.25f, repo.lastSaved!!.uiScale)
        assertNull(m.saveError)
    }

    @Test fun saveFailureKeepsInMemoryStateAndExposesError() {
        val repo = FakeRepo(failSave = true)
        val m = SettingsModel(repo)
        m.load()
        m.update { it.copy(copyOnSelect = true) }
        // 关键：不回滚内存态 —— 回滚会让用户以为白改了一次
        assertEquals(true, m.settings.copyOnSelect)
        assertNotNull(m.saveError, "写失败必须如实告知")
    }

    @Test fun recoveredLoadExposesNotice() {
        val repo = FakeRepo(
            SettingsLoad.Recovered(AppSettings.Default, "/tmp/settings.json.corrupt-1", "boom"),
        )
        val m = SettingsModel(repo)
        m.load()
        val notice = assertNotNull(m.loadNotice, "隔离恢复必须告知用户")
        assertTrue(notice.contains("corrupt"), "告知里应含副本文件名，实际：$notice")
        m.dismissLoadNotice()
        assertNull(m.loadNotice)
    }

    @Test fun okLoadWithWarningsExposesNotice() {
        val repo = FakeRepo(SettingsLoad.Ok(AppSettings.Default, listOf("界面缩放 99.0 无效，已改为 150%")))
        val m = SettingsModel(repo)
        m.load()
        assertNotNull(m.loadNotice, "有收敛警告也必须告知")
    }

    @Test fun dismissSaveErrorClearsIt() {
        val repo = FakeRepo(failSave = true)
        val m = SettingsModel(repo)
        m.load()
        m.update { it.copy(hideAddresses = true) }
        assertNotNull(m.saveError)
        m.dismissSaveError()
        assertNull(m.saveError)
    }
}
