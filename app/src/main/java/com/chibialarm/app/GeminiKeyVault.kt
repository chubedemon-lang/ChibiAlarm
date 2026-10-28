package com.chibialarm.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Per-install encryption key remains in Android Keystore; plaintext API key never persisted. */
object GeminiKeyVault {
    private const val ALIAS = "chibi_gemini_aes_v1"
    private const val PREF = "gemini_vault"
    private fun key(): java.security.Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.getKey(ALIAS, null)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun save(ctx: Context, profile: Int, value: String) {
        require(profile in 1..5)
        if (value.isBlank()) {
            ctx.getSharedPreferences(PREF,0).edit().remove("key_$profile").apply()
            return
        }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val encrypted=cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        val bytes=cipher.iv + encrypted
        ctx.getSharedPreferences(PREF,0).edit()
            .putString("key_$profile",Base64.encodeToString(bytes,Base64.NO_WRAP)).apply()
    }
    fun read(ctx: Context, profile: Int): String? {
        require(profile in 1..5)
        val value=ctx.getSharedPreferences(PREF,0).getString("key_$profile",null) ?: return null
        return try {
            val bytes=Base64.decode(value,Base64.DEFAULT)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
}
