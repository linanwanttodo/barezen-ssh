// shared/src/jvmTest/kotlin/com/barezen/ssh/settings/OpenSshConfigParserTest.kt
package com.barezen.ssh.settings

import com.barezen.ssh.servers.StoredAuth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenSshConfigParserTest {

    private val fixture = """
        # 这是注释
        Host web-01
            HostName 10.0.0.11
            User root
            Port 2222
            IdentityFile ~/.ssh/id_ed25519

        Host db-01 db-alias
            HostName 10.0.0.12
            User admin

        Host noportal
            HostName 10.0.0.13

        Host wild-*
            HostName 10.0.0.99
            User root

        Include ~/.ssh/extra_config

        Host bastion
            HostName 10.0.0.14
            User ops
            Port 2200
    """.trimIndent()

    @Test fun parsesBasicEntriesAndAliases() {
        val r = OpenSshConfigParser.parse(fixture)
        val names = r.added.map { it.name }
        // web-01 + db-01 + db-alias + bastion（noportal 缺 User 被跳过；wild-* 通配被跳过）
        assertEquals(listOf("web-01", "db-01", "db-alias", "bastion"), names)
    }

    @Test fun parsesFieldsPortAndAuth() {
        val r = OpenSshConfigParser.parse(fixture)
        val web = r.added.first { it.name == "web-01" }
        assertEquals("10.0.0.11", web.host)
        assertEquals(2222, web.port)
        assertEquals("root", web.user)
        assertTrue(web.auth is StoredAuth.Key)
        assertEquals("~/.ssh/id_ed25519", (web.auth as StoredAuth.Key).keyPath)

        val db = r.added.first { it.name == "db-01" }
        assertEquals("10.0.0.12", db.host)
        assertEquals(22, db.port, "缺 Port 时默认 22")
        assertEquals(StoredAuth.Password, db.auth, "缺 IdentityFile 时默认密码认证")
    }

    @Test fun skippedReasonsAreReported() {
        val r = OpenSshConfigParser.parse(fixture)
        assertEquals(1, r.skipped.count { it is SkipReason.NoUser })
        assertEquals(1, r.skipped.count { it is SkipReason.WildcardHost })
        assertTrue(r.includeSkipped, "遇到 Include 必须如实告知未跟随")
    }

    @Test fun hostNameFallsBackToFirstAlias() {
        val text = "Host myalias\n    User root\n"
        val r = OpenSshConfigParser.parse(text)
        assertEquals("myalias", r.added.single().host, "无 HostName 时 host = 第一个别名")
    }
}
