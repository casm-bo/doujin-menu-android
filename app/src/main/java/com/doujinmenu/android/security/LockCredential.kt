package com.doujinmenu.android.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class LockMethod(val label: String) { PIN("PIN"), PASSWORD("비밀번호"), PATTERN("패턴") }

data class LockCredential(val method: LockMethod, val salt: ByteArray, val hash: ByteArray, val biometric: Boolean = false) {
    fun matches(secret: String): Boolean = MessageDigest.isEqual(hash, derive(secret, salt))
    companion object {
        fun create(method: LockMethod, secret: String): LockCredential {
            require(validLockSecret(method, secret)) { "잠금 입력 형식을 확인하세요." }
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            return LockCredential(method, salt, derive(secret, salt))
        }
        private fun derive(secret: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(secret.toCharArray(), salt, 210_000, 256)
            return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
                finally { spec.clearPassword() }
        }
    }
}

fun validLockSecret(method: LockMethod, value: String): Boolean = when (method) {
    LockMethod.PIN -> value.length in 4..12 && value.all { it in '0'..'9' }
    LockMethod.PASSWORD -> value.length in 6..128 && value.isNotBlank()
    LockMethod.PATTERN -> value.length in 4..9 && value.all { it in '0'..'8' } && value.toSet().size == value.length
}

fun appendPatternPoint(path: String, point: Int): String {
    if (point !in 0..8 || point.digitToChar() in path) return path
    val previous = path.lastOrNull()?.digitToInt() ?: return point.toString()
    val dx = point % 3 - previous % 3
    val dy = point / 3 - previous / 3
    val middle = if (dx % 2 == 0 && dy % 2 == 0) ((point / 3 + previous / 3) / 2) * 3 + (point % 3 + previous % 3) / 2 else -1
    val prefix = if (middle in 0..8 && middle != previous && middle != point && middle.digitToChar() !in path) path + middle else path
    return prefix + point
}
