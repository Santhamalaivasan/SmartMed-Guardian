package com.smartmedguardian.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val provider = "AndroidKeyStore"
    private val alias = "SmartMedGuardianKey"
    private val transformation = "AES/GCM/NoPadding"

    init {
        generateKey()
    }

    private fun generateKey() {
        val keyStore = KeyStore.getInstance(provider)
        keyStore.load(null)
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(provider)
        keyStore.load(null)
        return keyStore.getKey(alias, null) as SecretKey
    }

    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        // Combine IV and encrypted data: [IV_LENGTH][IV][ENCRYPTED_DATA]
        val result = ByteArray(1 + iv.size + encryptedData.size)
        result[0] = iv.size.toByte()
        System.arraycopy(iv, 0, result, 1, iv.size)
        System.arraycopy(encryptedData, 0, result, 1 + iv.size, encryptedData.size)
        
        return Base64.encodeToString(result, Base64.DEFAULT)
    }

    fun decrypt(encryptedBase64: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val ivSize = combined[0].toInt()
        val iv = ByteArray(ivSize)
        System.arraycopy(combined, 1, iv, 0, ivSize)
        
        val encryptedData = ByteArray(combined.size - 1 - ivSize)
        System.arraycopy(combined, 1 + ivSize, encryptedData, 0, encryptedData.size)
        
        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        
        return String(cipher.doFinal(encryptedData), Charsets.UTF_8)
    }
}
