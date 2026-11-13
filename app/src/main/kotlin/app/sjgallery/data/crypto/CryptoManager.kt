package app.sjgallery.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.sjgallery.data.storage.readAtomicallyOrNull
import app.sjgallery.data.storage.writeAtomically
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated encryption (AES-256-GCM) for everything the app stores.
 *
 * Uses envelope encryption:
 *  - a random 256-bit *data key* encrypts files in-process, which is fast
 *    enough for large images;
 *  - the data key is itself stored on disk encrypted ("wrapped") by a key that
 *    is generated inside, and never leaves, the Android Keystore.
 *
 * Without the Keystore key, which is bound to this app on this device, the
 * files in app storage are indistinguishable from random bytes.
 *
 * Blob layout: `[version: 1 byte][nonce: 12 bytes][ciphertext + 16-byte tag]`.
 */
class CryptoManager(private val wrappedKeyFile: File) {

    private val secureRandom = SecureRandom()

    // Lazy so the Keystore round trip happens off the main thread, on first use.
    private val dataKey: SecretKey by lazy { loadOrCreateDataKey() }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, dataKey, GCMParameterSpec(TAG_BITS, nonce))

        val blob = ByteArray(HEADER_BYTES + cipher.getOutputSize(plaintext.size))
        blob[0] = FORMAT_VERSION
        nonce.copyInto(blob, destinationOffset = 1)
        cipher.doFinal(plaintext, 0, plaintext.size, blob, HEADER_BYTES)
        return blob
    }

    /** @throws java.security.GeneralSecurityException if the blob was tampered with or is corrupt. */
    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > HEADER_BYTES && blob[0] == FORMAT_VERSION) {
            "Not an encrypted blob this version understands"
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, dataKey, GCMParameterSpec(TAG_BITS, blob, 1, NONCE_BYTES))
        return cipher.doFinal(blob, HEADER_BYTES, blob.size - HEADER_BYTES)
    }

    private fun loadOrCreateDataKey(): SecretKey {
        val wrappingKey = loadOrCreateWrappingKey()

        wrappedKeyFile.readAtomicallyOrNull()?.let { wrapped ->
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                wrappingKey,
                GCMParameterSpec(TAG_BITS, wrapped, 0, NONCE_BYTES),
            )
            val raw = cipher.doFinal(wrapped, NONCE_BYTES, wrapped.size - NONCE_BYTES)
            return SecretKeySpec(raw, KeyProperties.KEY_ALGORITHM_AES)
        }

        val raw = ByteArray(DATA_KEY_BYTES).also(secureRandom::nextBytes)
        // The Keystore insists on choosing the nonce itself for its own keys.
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        wrappedKeyFile.writeAtomically(cipher.iv + cipher.doFinal(raw))
        return SecretKeySpec(raw, KeyProperties.KEY_ALGORITHM_AES)
    }

    private fun loadOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            WRAPPING_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val WRAPPING_KEY_ALIAS = "sjgallery.wrapping-key.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val DATA_KEY_BYTES = 32
        const val HEADER_BYTES = 1 + NONCE_BYTES
    }
}
