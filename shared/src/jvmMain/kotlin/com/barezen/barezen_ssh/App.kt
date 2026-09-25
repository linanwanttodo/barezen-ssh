// shared/src/commonMain/kotlin/com/barezen/barezen_ssh/App.kt（整体替换）
package com.barezen.barezen_ssh

import androidx.compose.runtime.Composable
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.ui.shell.BareZenAppContent
import com.barezen.barezen_ssh.ui.theme.BareZenTheme

@Composable
fun App(model: AppModel) = BareZenTheme { BareZenAppContent(model) }
