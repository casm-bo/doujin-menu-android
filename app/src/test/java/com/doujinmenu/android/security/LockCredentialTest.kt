package com.doujinmenu.android.security

import org.junit.Assert.*
import org.junit.Test

class LockCredentialTest {
    @Test fun `credentials use random salt and reject wrong secrets`() {
        val first = LockCredential.create(LockMethod.PIN, "123456")
        val second = LockCredential.create(LockMethod.PIN, "123456")
        assertFalse(first.salt.contentEquals(second.salt))
        assertTrue(first.matches("123456"))
        assertFalse(first.matches("654321"))
        assertFalse(validLockSecret(LockMethod.PIN, "123"))
        assertFalse(validLockSecret(LockMethod.PASSWORD, "short"))
        assertFalse(validLockSecret(LockMethod.PATTERN, "0012"))
    }
    @Test fun `pattern jumps add unvisited middle dots and never repeat points`() {
        assertEquals("012", appendPatternPoint("0", 2))
        assertEquals("048", appendPatternPoint("0", 8))
        assertEquals("012", appendPatternPoint("012", 1))
        assertEquals("04", appendPatternPoint("0", 4))
    }
}
