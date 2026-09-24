package com.barezen.barezen_ssh

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform