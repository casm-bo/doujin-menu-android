package com.doujinmenu.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

class AppLockStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    val enabled get() = preferences.contains("credential")
    fun read(): LockCredential? {
        if (!enabled) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(preferences.getString("iv", "")!!)))
        val json = JSONObject(cipher.doFinal(decode(preferences.getString("credential", "")!!)).toString(Charsets.UTF_8))
        return LockCredential(LockMethod.valueOf(json.getString("method")), decode(json.getString("salt")), decode(json.getString("hash")), json.optBoolean("biometric"))
    }
    fun save(value: LockCredential) {
        val json = JSONObject().put("method", value.method.name).put("salt", encode(value.salt)).put("hash", encode(value.hash)).put("biometric", value.biometric)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        check(preferences.edit().putString("credential", encode(cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))))
            .putString("iv", encode(cipher.iv)).commit()) { "잠금 설정 저장에 실패했습니다." }
    }
    fun clear() { check(preferences.edit().clear().commit()) }
    fun remainingDelay(now: Long): Long = (preferences.getLong("blocked_until", 0) - now).coerceAtLeast(0)
    fun failed(now: Long) {
        val attempts = preferences.getInt("attempts", 0) + 1
        check(preferences.edit().putInt("attempts", if (attempts >= 5) 0 else attempts)
            .putLong("blocked_until", if (attempts >= 5) now + 30_000 else 0).commit())
    }
    fun resetAttempts() { check(preferences.edit().remove("attempts").remove("blocked_until").commit()) }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("doujin_app_lock", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("doujin_app_lock", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String) = Base64.getDecoder().decode(value)
}
