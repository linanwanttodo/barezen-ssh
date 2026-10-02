// shared/src/jvmTest/kotlin/com/barezen/ssh/settings/UpdateCheckerTest.kt
package com.barezen.ssh.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun releases(vararg items: String) = items.joinToString(",", "[", "]")

class UpdateCheckerTest {

    private val stableJson = releases(
        """{"tag_name":"v0.3.0","prerelease":true,"draft":false,"html_url":"u3"}""",
        """{"tag_name":"v0.2.0","prerelease":false,"draft":false,"html_url":"u2"}""",
        """{"tag_name":"v0.1.5","prerelease":false,"draft":true,"html_url":"u15"}""",
    )

    @Test fun stablePicksLatestNonPrereleaseNonDraft() = runBlocking<Unit> {
        val c = UpdateChecker { stableJson }
        val r = c.check("acme/repo", UpdateChannel.STABLE, "0.1.0")
        val ok = assertIs<UpdateResult.NewerAvailable>(r)
        assertEquals("0.2.0", ok.latest)
        assertEquals("u2", ok.url)
    }

    @Test fun previewMayPickPrerelease() = runBlocking<Unit> {
        val c = UpdateChecker { stableJson }
        val r = c.check("acme/repo", UpdateChannel.PREVIEW, "0.1.0")
        assertEquals("0.3.0", assertIs<UpdateResult.NewerAvailable>(r).latest)
    }

    @Test fun notConfiguredMakesNoRequest() = runBlocking<Unit> {
        var calls = 0
        val c = UpdateChecker { calls++; "[]" }
        val r = c.check("", UpdateChannel.STABLE, "0.1.0")
        assertIs<UpdateResult.NotConfigured>(r)
        assertEquals(0, calls, "未配置更新源时不得发起任何请求")
    }

    @Test fun sameVersionIsUpToDate() = runBlocking<Unit> {
        val c = UpdateChecker { releases("""{"tag_name":"v0.1.0","prerelease":false,"draft":false,"html_url":"u"}""") }
        assertIs<UpdateResult.UpToDate>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
    }

    @Test fun v1_0_0AgainstSameTagIsUpToDate() = runBlocking<Unit> {
        val c = UpdateChecker { releases("""{"tag_name":"v1.0.0","prerelease":false,"draft":false,"html_url":"u"}""") }
        assertIs<UpdateResult.UpToDate>(c.check("linanwanttodo/barezen-ssh", UpdateChannel.STABLE, "1.0.0"))
    }

    @Test fun v1_0_0AgainstNewerTagOffersUpdate() = runBlocking<Unit> {
        val c = UpdateChecker { releases("""{"tag_name":"v1.0.1","prerelease":false,"draft":false,"html_url":"u"}""") }
        val r = assertIs<UpdateResult.NewerAvailable>(c.check("linanwanttodo/barezen-ssh", UpdateChannel.STABLE, "1.0.0"))
        assertEquals("1.0.1", r.latest)
    }

    @Test fun olderVersionIsUpToDate() = runBlocking<Unit> {
        val c = UpdateChecker { releases("""{"tag_name":"v0.0.9","prerelease":false,"draft":false,"html_url":"u"}""") }
        assertIs<UpdateResult.UpToDate>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
    }

    @Test fun vPrefixIsStripped() = runBlocking<Unit> {
        val c = UpdateChecker { releases("""{"tag_name":"0.2.0","prerelease":false,"draft":false,"html_url":"u"}""") }
        assertEquals("0.2.0", assertIs<UpdateResult.NewerAvailable>(c.check("a/b", UpdateChannel.STABLE, "0.1.0")).latest)
    }

    @Test fun nonSemverIsUncomparableNotLiedAbout() = runBlocking<Unit> {
        val c = UpdateChecker { releases("""{"tag_name":"release-2026","prerelease":false,"draft":false,"html_url":"u"}""") }
        assertIs<UpdateResult.Uncomparable>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
    }

    @Test fun emptyReleaseListIsUpToDate() = runBlocking<Unit> {
        val c = UpdateChecker { "[]" }
        assertIs<UpdateResult.UpToDate>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
    }

    @Test fun httpErrorMapsToFriendlyMessage() = runBlocking<Unit> {
        val c = UpdateChecker { throw java.io.IOException("404 Not Found") }
        val f = assertIs<UpdateResult.Failed>(c.check("a/b", UpdateChannel.STABLE, "0.1.0"))
        assertEquals(true, f.message.isNotBlank())
    }
}
