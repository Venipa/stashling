package net.venipa.stashling.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM (from wallcrad); blob = 12-byte IV + ciphertext + tag. [open] returns null on any failure (wrong key, tampering, truncation). */
internal class SecretBox(private val key: Lazy<SecretKey>) {
    /** The key lives in the Android Keystore under [alias] and never leaves it. */
    constructor(alias: String = "stashling.secrets") : this(lazy { keystoreKey(alias) })

    fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key.value) }
        return cipher.iv + cipher.doFinal(plain)
    }

    fun open(blob: ByteArray): ByteArray? = try {
        if (blob.size <= IV_BYTES) {
            null
        } else {
            Cipher.getInstance(TRANSFORM).run {
                init(Cipher.DECRYPT_MODE, key.value, GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
                doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
            }
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        fun keystoreKey(alias: String): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(alias, null) as? SecretKey)?.let { return it }
            return KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec
                            .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build(),
                    )
                }.generateKey()
        }
    }
}
