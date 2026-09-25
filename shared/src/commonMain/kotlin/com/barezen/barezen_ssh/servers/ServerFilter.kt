package com.barezen.barezen_ssh.servers

fun filterServers(servers: List<Server>, query: String, tag: String?): List<Server> {
    val q = query.trim()
    return servers.filter { s ->
        (tag == null || tag in s.tags) &&
            (q.isEmpty() || q.lowercase() in s.name.lowercase() || q.lowercase() in s.host.lowercase() || s.tags.any { q.lowercase() in it.lowercase() })
    }
}
