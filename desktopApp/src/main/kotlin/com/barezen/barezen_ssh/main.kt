package com.barezen.barezen_ssh

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BareZen-SSH",
    ) {
        App()
    }
}