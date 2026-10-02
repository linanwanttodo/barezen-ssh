// shared/src/jvmMain/kotlin/com/barezen/ssh/App.kt
package com.barezen.ssh

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import com.barezen.ssh.app.AppModel
import com.barezen.ssh.settings.Theme
import com.barezen.ssh.ui.shell.BareZenAppContent
import com.barezen.ssh.ui.theme.BareZenTheme
import com.barezen.ssh.ui.theme.scaledDensity

@Composable
fun App(model: AppModel) {
    val settings = model.settings.settings
    // 浅色板尚未实现，故 FOLLOW_SYSTEM 与 DARK 目前都解析为深色。
    // 保留 when 分支：浅色落地时只改这一处。
    val dark = when (settings.theme) {
        Theme.DARK -> true
        Theme.FOLLOW_SYSTEM -> isSystemInDarkTheme() || true
    }
    BareZenTheme(darkTheme = dark) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides scaledDensity(base, settings.uiScale),
        ) {
            BareZenAppContent(model)
        }
    }
}
