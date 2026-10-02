package com.barezen.ssh.ssh.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 远端条目名校验穷举：合法 / 空 / 空白 / 分隔符 / 目录导航名 / 超长。 */
class RemoteNameValidatorTest {

    @Test fun acceptsOrdinaryNames() {
        listOf("a.txt", "报告.docx", "dir", "a b", "a-b_c.d", "..a", "a..", "测试文件-中文名.txt", ".hidden")
            .forEach { assertNull(RemoteNameValidator.validate(it), "应接受：$it") }
    }

    @Test fun rejectsEmptyAndBlank() {
        assertEquals("名称不能为空", RemoteNameValidator.validate(""))
        assertEquals("名称不能为空", RemoteNameValidator.validate("   "))
        assertEquals("名称不能为空", RemoteNameValidator.validate(null))
        assertEquals("名称不能为空", RemoteNameValidator.validate("	"))
    }

    @Test fun rejectsSlash() {
        assertEquals("名称不能包含 /", RemoteNameValidator.validate("a/b"))
        assertEquals("名称不能包含 /", RemoteNameValidator.validate("/abs"))
        assertEquals("名称不能包含 /", RemoteNameValidator.validate("trailing/"))
    }

    @Test fun rejectsBackslash() {
        assertTrue(RemoteNameValidator.validate("a\\b")!!.contains("\\"))
    }

    @Test fun rejectsDotAndDotDot() {
        assertEquals("名称不能是 . 或 ..", RemoteNameValidator.validate("."))
        assertEquals("名称不能是 . 或 ..", RemoteNameValidator.validate(".."))
        // 前后空白先 trim 再判定
        assertEquals("名称不能是 . 或 ..", RemoteNameValidator.validate("  ..  "))
    }

    @Test fun trimsBeforeValidating() {
        assertNull(RemoteNameValidator.validate("  a.txt  "))
        assertEquals("名称不能包含 /", RemoteNameValidator.validate("  a/b  "))
    }

    @Test fun isValidMirrorsValidate() {
        assertTrue(RemoteNameValidator.isValid("ok.txt"))
        assertFalse(RemoteNameValidator.isValid(""))
        assertFalse(RemoteNameValidator.isValid("a/b"))
    }

    @Test fun handlesVeryLongName() {
        val long = "x".repeat(4096)
        assertNull(RemoteNameValidator.validate(long), "长度不由本校验负责（交服务端裁决）")
    }
}
