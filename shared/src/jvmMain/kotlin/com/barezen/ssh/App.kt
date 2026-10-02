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
    val dark = when (settings.theme) {
        Theme.DARK -> true
        Theme.LIGHT -> false
        Theme.FOLLOW_SYSTEM -> isSystemInDarkTheme()
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
