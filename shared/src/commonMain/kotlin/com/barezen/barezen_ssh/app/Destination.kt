package com.barezen.barezen_ssh.app

enum class Destination(val label: String) {
    SERVERS("服务器"), TERMINAL("终端"), FILES("文件"),
    DASHBOARD("仪表盘"), PORTS("端口转发"), SETTINGS("设置")
}
