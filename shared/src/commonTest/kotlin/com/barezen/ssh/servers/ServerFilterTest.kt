// shared/src/commonTest/kotlin/com/barezen/ssh/servers/ServerFilterTest.kt
package com.barezen.ssh.servers

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerFilterTest {
    private val web = Server("1", "web-01", "10.0.0.11", 22, "root", listOf("生产", "nginx"))
    private val db = Server("2", "db-01", "10.0.0.12", 22, "root", listOf("生产", "PostgreSQL"))
    private val edge = Server("3", "edge-02", "45.32.18.7", 2222, "admin", listOf("边缘"))
    private val all = listOf(web, db, edge)

    @Test fun emptyQueryAndNoTagReturnsAll() { assertEquals(all, filterServers(all, "", null)) }
    @Test fun queryMatchesNameIgnoringCase() { assertEquals(listOf(web), filterServers(all, "WEB", null)) }
    @Test fun queryMatchesHost() { assertEquals(listOf(db), filterServers(all, "10.0.0.12", null)) }
    @Test fun queryMatchesTag() { assertEquals(listOf(edge), filterServers(all, "边缘", null)) }
    @Test fun tagFiltersExactly() { assertEquals(listOf(web, db), filterServers(all, "", "生产")) }
    @Test fun queryAndTagCombine() { assertEquals(listOf(db), filterServers(all, "db", "生产")) }
    @Test fun noMatchReturnsEmpty() { assertEquals(emptyList(), filterServers(all, "nope", null)) }
}
