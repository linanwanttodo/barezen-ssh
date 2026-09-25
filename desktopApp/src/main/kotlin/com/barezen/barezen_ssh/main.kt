package com.barezen.barezen_ssh

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.app.real

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BareZen-SSH",
        state = rememberWindowState(width = 1440.dp, height = 900.dp),
    ) {
        App(AppModel.real())
    }
}
