package com.barezen.barezen_ssh

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.barezen.barezen_ssh.app.AppModel
import com.barezen.barezen_ssh.app.real

fun main() = application {
    // remember：模型承载连接态与 shell 会话，重组静默重建会把活动会话晾成孤儿
    val model = remember { AppModel.real() }
    Window(
        // 关窗即收口会话（先 close 再退出，SSH 连接/keep-alive 线程不残留）
        onCloseRequest = {
            model.disconnect()
            exitApplication()
        },
        title = "BareZen-SSH",
        state = rememberWindowState(width = 1440.dp, height = 900.dp),
    ) {
        App(model)
    }
}
