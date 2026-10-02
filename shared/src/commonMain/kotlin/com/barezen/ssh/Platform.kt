package com.barezen.ssh

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform