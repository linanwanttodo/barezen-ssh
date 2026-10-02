// shared/src/commonMain/kotlin/com/barezen/ssh/servers/Server.kt
package com.barezen.ssh.servers

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Server(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val tags: List<String> = emptyList(),
    val auth: StoredAuth = StoredAuth.Password,
)

@Serializable
sealed interface StoredAuth {
    @Serializable
    @SerialName("password")
    data object Password : StoredAuth

    @Serializable
    @SerialName("key")
    data class Key(val keyPath: String) : StoredAuth
}
