// shared/src/jvmTest/kotlin/com/barezen/ssh/ui/ServersScreenCredentialsTest.kt
package com.barezen.ssh.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.barezen.ssh.servers.Server
import com.barezen.ssh.ssh.AuthMethod
import com.barezen.ssh.ui.screens.CONNECT_REMEMBER_TAG
import com.barezen.ssh.ui.screens.ConnectDialog
import com.barezen.ssh.ui.theme.BareZenTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ConnectDialog 的钥匙串接线断言（纯展示组件，直接组合，不经壳层）：
 * 勾选/未勾选的回传值、钥匙串不可用回退、prefill 预填与「已存入系统钥匙串」提示。
 *
 * 安全说明文案三分支互斥：默认（钥匙串可用且未勾选）保留内存口径；
 * 勾选后改「凭据将存入系统钥匙串。」；钥匙串不可用走明示不保存口径。
 */
@OptIn(ExperimentalTestApi::class)
class ServersScreenCredentialsTest {

    private val web01 = Server("1", "web-01", "10.0.0.11", 22, "root")

    @Test
    fun checkingRememberCredentialReportsTrue() = runComposeUiTest {
        var result: Pair<AuthMethod?, Boolean>? = null
        setContent {
            BareZenTheme {
                ConnectDialog(
                    server = web01,
                    keychainAvailable = true,
                    onResult = { auth, remember -> result = auth to remember },
                )
            }
        }
        onNodeWithTag(CONNECT_REMEMBER_TAG).performClick()
        // 勾选后安全口径随之改口，不得再出现「不会写入本地文件」
        onNodeWithText("勾选后，凭据将存入系统钥匙串。").assertIsDisplayed()
        assertTrue(onAllNodesWithText("密码仅保存在内存中，不会写入本地文件。").fetchSemanticsNodes().isEmpty())
        onNodeWithText("连接").performClick()
        val (auth, remember) = result ?: error("onResult 未被调用")
        assertEquals(true, remember)
        assertEquals(AuthMethod.Password(""), auth)
    }

    @Test
    fun uncheckedRememberCredentialReportsFalse() = runComposeUiTest {
        var result: Pair<AuthMethod?, Boolean>? = null
        setContent {
            BareZenTheme {
                ConnectDialog(
                    server = web01,
                    keychainAvailable = true,
                    onResult = { auth, remember -> result = auth to remember },
                )
            }
        }
        onNodeWithText("记住凭据").assertIsDisplayed()
        onNodeWithText("密码仅保存在内存中，不会写入本地文件。").assertIsDisplayed()
        onNodeWithText("连接").performClick()
        val (auth, remember) = result ?: error("onResult 未被调用")
        assertEquals(false, remember)
        assertEquals(AuthMethod.Password(""), auth)
    }

    @Test
    fun unavailableKeychainHidesCheckboxAndShowsFallbackNote() = runComposeUiTest {
        var result: Pair<AuthMethod?, Boolean>? = null
        setContent {
            BareZenTheme {
                ConnectDialog(
                    server = web01,
                    keychainAvailable = false,
                    onResult = { auth, remember -> result = auth to remember },
                )
            }
        }
        assertTrue(onAllNodesWithTag(CONNECT_REMEMBER_TAG).fetchSemanticsNodes().isEmpty())
        onNodeWithText("系统钥匙串不可用，凭据不会保存").assertIsDisplayed()
        onNodeWithText("密码仅保存在内存中；本机钥匙串不可用，凭据不会保存。").assertIsDisplayed()
        onNodeWithText("连接").performClick()
        assertEquals(false, result?.second)
    }

    @Test
    fun cancelReportsNullAndNoRemember() = runComposeUiTest {
        var result: Pair<AuthMethod?, Boolean>? = null
        setContent {
            BareZenTheme {
                ConnectDialog(
                    server = web01,
                    keychainAvailable = true,
                    onResult = { auth, remember -> result = auth to remember },
                )
            }
        }
        onNodeWithText("取消").performClick()
        assertNull(result?.first)
        assertEquals(false, result?.second)
    }

    @Test
    fun prefillFillsPasswordFieldAndShowsKeychainBadge() = runComposeUiTest {
        setContent {
            BareZenTheme {
                ConnectDialog(
                    server = web01,
                    prefill = AuthMethod.Password("s3cret"),
                    keychainAvailable = true,
                    onResult = { _, _ -> },
                )
            }
        }
        onNodeWithText("已存入系统钥匙串").assertIsDisplayed()
        // 密码字段是唯一的 6 字符掩码节点（其他字段为空）
        onNodeWithText("••••••").assertIsDisplayed()
        // 预填不代替用户选择：勾选框仍默认未勾选
        onNodeWithText("密码仅保存在内存中，不会写入本地文件。").assertIsDisplayed()
    }

    @Test
    fun prefillFillsKeyPathAndSuppliesStoredPassphrase() = runComposeUiTest {
        var result: Pair<AuthMethod?, Boolean>? = null
        val keyServer = Server(
            id = "2",
            name = "key-01",
            host = "10.0.0.12",
            port = 22,
            user = "root",
            auth = com.barezen.ssh.servers.StoredAuth.Key("/home/lin/.ssh/id_ed25519"),
        )
        setContent {
            BareZenTheme {
                ConnectDialog(
                    server = keyServer,
                    prefill = AuthMethod.PrivateKey("/home/lin/.ssh/id_ed25519", "pp"),
                    keychainAvailable = true,
                    onResult = { auth, remember -> result = auth to remember },
                )
            }
        }
        onNodeWithText("已存入系统钥匙串").assertIsDisplayed()
        onNodeWithText("/home/lin/.ssh/id_ed25519").assertIsDisplayed()
        // 口令不回显明文：钥匙串口令留到确认时回填
        onNodeWithText("pp").assertDoesNotExist()
        onNodeWithText("连接").performClick()
        assertEquals(AuthMethod.PrivateKey("/home/lin/.ssh/id_ed25519", "pp"), result?.first)
        assertEquals(false, result?.second)
    }
}
