package com.barezen.ssh.app

enum class Destination(val label: String) {
    DASHBOARD("仪表盘"), SERVERS("服务器"), TERMINAL("终端"),
    FILES("文件"), PORTS("端口转发"), SETTINGS("设置")
}
