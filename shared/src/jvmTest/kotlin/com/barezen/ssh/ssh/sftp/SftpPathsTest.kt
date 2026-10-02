// shared/src/jvmTest/kotlin/com/barezen/ssh/ssh/sftp/SftpPathsTest.kt
package com.barezen.ssh.ssh.sftp

import kotlin.test.Test
import kotlin.test.assertEquals

class SftpPathsTest {

    // ---- join：合法 ----

    @Test fun joinRootChild() = assertEquals("/a", SftpPaths.join("/", "a"))
    @Test fun joinNested() = assertEquals("/a/b", SftpPaths.join("/a", "b"))
    @Test fun joinTrailingSlashParent() = assertEquals("/a/b", SftpPaths.join("/a/", "b"))
    @Test fun joinRootTrailingSlash() = assertEquals("/a", SftpPaths.join("//", "a"))
    @Test fun joinDeep() = assertEquals("/var/log/syslog", SftpPaths.join("/var/log", "syslog"))

    // ---- join：边界/非法 ----

    @Test fun joinEmptyNameReturnsParent() = assertEquals("/a", SftpPaths.join("/a", ""))
    @Test fun joinNullParentTreatedAsRoot() = assertEquals("/a", SftpPaths.join(null, "a"))
    @Test fun joinEmptyParentTreatedAsRoot() = assertEquals("/a", SftpPaths.join("", "a"))
    @Test fun joinEmptyNameOnRoot() = assertEquals("/", SftpPaths.join("/", ""))

    // ---- parentOf：合法 ----

    @Test fun parentOfNested() = assertEquals("/a", SftpPaths.parentOf("/a/b"))
    @Test fun parentOfDeep() = assertEquals("/a/b", SftpPaths.parentOf("/a/b/c"))
    @Test fun parentOfDirectChild() = assertEquals("/", SftpPaths.parentOf("/a"))
    @Test fun parentOfTrailingSlash() = assertEquals("/a", SftpPaths.parentOf("/a/b/"))

    // ---- parentOf：边界/非法 ----

    @Test fun parentOfRoot() = assertEquals("/", SftpPaths.parentOf("/"))
    @Test fun parentOfEmpty() = assertEquals("/", SftpPaths.parentOf(""))
    @Test fun parentOfNull() = assertEquals("/", SftpPaths.parentOf(null))
    @Test fun parentOfRootTrailingSlash() = assertEquals("/", SftpPaths.parentOf("//"))

    // ---- nameOf：合法与边界 ----

    @Test fun nameOfNested() = assertEquals("b", SftpPaths.nameOf("/a/b"))
    @Test fun nameOfDirectChild() = assertEquals("a", SftpPaths.nameOf("/a"))
    @Test fun nameOfTrailingSlash() = assertEquals("b", SftpPaths.nameOf("/a/b/"))
    @Test fun nameOfRoot() = assertEquals("/", SftpPaths.nameOf("/"))
    @Test fun nameOfEmpty() = assertEquals("/", SftpPaths.nameOf(""))
    @Test fun nameOfBareSegment() = assertEquals("b", SftpPaths.nameOf("b"))
}
