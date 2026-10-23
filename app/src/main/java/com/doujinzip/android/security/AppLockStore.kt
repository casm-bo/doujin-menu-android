package com.doujinzip.android.security

import com.doujinzip.android.R

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

class AppLockStore(private val context: Context, private val preferenceName: String = "app_lock") {
    private val strings get() = androidx.core.content.ContextCompat.getContextForLanguage(context).resources
    private val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
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
            .putString("iv", encode(cipher.iv)).commit()) { strings.getString(R.string.msg_could_not_save_lock_settings) }
    }
    fun clear() { check(preferences.edit().clear().commit()) }
    private fun attempts() = LockAttempts(preferences.getInt("attempts", 0).coerceIn(0, 4), preferences.getLong("blocked_until", 0).coerceAtLeast(0))
    fun remainingDelay(now: Long): Long = attempts().remaining(now)
    fun failed(now: Long) {
        val next = attempts().failed(now)
        check(preferences.edit().putInt("attempts", next.count).putLong("blocked_until", next.blockedUntil).commit())
    }
    fun resetAttempts() { check(preferences.edit().remove("attempts").remove("blocked_until").commit()) }
    private fun key(): SecretKey {
        val alias = if (preferenceName == "app_lock") "doujin_app_lock" else "doujin_$preferenceName"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String) = Base64.getDecoder().decode(value)
}
