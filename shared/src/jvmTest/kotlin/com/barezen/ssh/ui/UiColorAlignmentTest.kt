// shared/src/jvmTest/kotlin/com/barezen/ssh/ui/UiColorAlignmentTest.kt
package com.barezen.ssh.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.Server
import com.barezen.ssh.ui.screens.EmptyHint
import com.barezen.ssh.ui.screens.ServerCard
import com.barezen.ssh.ui.theme.BareZenAccent
import com.barezen.ssh.ui.theme.BareZenAccentSubtle
import com.barezen.ssh.ui.theme.BareZenDarkColors
import com.barezen.ssh.ui.theme.BareZenLightAccent
import com.barezen.ssh.ui.theme.BareZenLightAccentSubtle
import com.barezen.ssh.ui.theme.BareZenLightColors
import com.barezen.ssh.ui.theme.BareZenOnAccent
import com.barezen.ssh.ui.theme.BareZenLightOnAccent
import com.barezen.ssh.ui.theme.BareZenTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 配色终审（STATUS 3.4，T-6 批次一）锁定测试：
 * - 统一空态组件 EmptyHint 的形态契约（tag + 文案）；
 * - 服务器卡状态点两态（已连接实心 / 未连接空心）以 tag 锁定语义分支；
 * - tertiary 槽位不再携带彩色语义（tertiary = 中性灰阶，与 primary 同源）。
 */
class UiColorAlignmentTest {

    // ---- EmptyHint：统一空态 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun emptyHintShowsIconAndText() = runComposeUiTest {
        setContent { BareZenTheme { EmptyHint(Icons.Outlined.Folder, "连接后可管理文件") } }
        onNodeWithTag("empty-hint").assertIsDisplayed()
        onNodeWithText("连接后可管理文件").assertIsDisplayed()
    }

    // ---- 服务器卡状态点：形状区分（实心 / 空心），以 tag 锁定两态分支 ----

    @OptIn(ExperimentalTestApi::class)
    @Test fun connectedCardShowsFilledDot() = runComposeUiTest {
        val up = Server("1", "web-01", "10.0.0.11", 22, "root")
        setContent {
            BareZenTheme {
                ServerCard(
                    server = up,
                    hideAddresses = false,
                    connected = true,
                    activeLatencyMs = 24L,
                    sessionCount = 1,
                    onConnect = {}, onNewTerminal = {}, onOpenFiles = {}, onEdit = {},
                )
            }
        }
        onNodeWithTag("server-status-dot-connected").assertIsDisplayed()
        onNodeWithText("已连接 · 24 ms").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun disconnectedCardShowsHollowDot() = runComposeUiTest {
        val down = Server("1", "web-01", "10.0.0.11", 22, "root")
        setContent {
            BareZenTheme {
                ServerCard(
                    server = down,
                    hideAddresses = false,
                    connected = false,
                    activeLatencyMs = null,
                    sessionCount = 0,
                    onConnect = {}, onNewTerminal = {}, onOpenFiles = {}, onEdit = {},
                )
            }
        }
        onNodeWithTag("server-status-dot-disconnected").assertIsDisplayed()
        onNodeWithText("未连接").assertIsDisplayed()
    }

    // ---- tertiary 槽位中性化：不再指向 BareZenInfo/BareZenInfoBg ----

    @Test fun darkTertiaryIsNeutralAccent() {
        val c = BareZenDarkColors
        assertEquals(BareZenAccent, c.tertiary)
        assertEquals(BareZenAccentSubtle, c.tertiaryContainer)
        assertEquals(BareZenOnAccent, c.onTertiaryContainer)
    }

    @Test fun lightTertiaryIsNeutralAccent() {
        val c = BareZenLightColors
        assertEquals(BareZenLightAccent, c.tertiary)
        assertEquals(BareZenLightAccentSubtle, c.tertiaryContainer)
        assertEquals(BareZenLightOnAccent, c.onTertiaryContainer)
    }
}
