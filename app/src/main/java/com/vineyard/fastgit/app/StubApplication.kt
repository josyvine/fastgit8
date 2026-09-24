package com.vineyard.fastgit.app

import android.app.Application
import android.content.Context
import dalvik.system.InMemoryDexClassLoader
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class StubApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        loadAndInjectEncryptedDex(base)
    }

    private fun loadAndInjectEncryptedDex(context: Context) {
        try {
            // 1. Read encrypted asset payload into RAM
            val assetManager = context.assets
            val assetList = assetManager.list("") ?: emptyArray()
            
            if (!assetList.contains(PAYLOAD_ASSET_NAME)) {
                // If payload.bin does not exist (e.g. unencrypted debug run), do not interrupt
                return
            }

            val rawBytes: ByteArray = assetManager.open(PAYLOAD_ASSET_NAME).use { inputStream: InputStream ->
                inputStream.readBytes()
            }

            // Minimum length check: 8 bytes OpenSSL magic ("Salted__") + 8 bytes salt + AES block (16 bytes)
            if (rawBytes.size <= 32) {
                return
            }

            // Verify OpenSSL magic header
            val headerString = String(rawBytes, 0, 8, StandardCharsets.US_ASCII)
            if (headerString != OPENSSL_MAGIC_HEADER) {
                return
            }

            // 2. Extract Salt (8 bytes starting at offset 8)
            val salt = ByteArray(8)
            System.arraycopy(rawBytes, 8, salt, 0, 8)

            // 3. Extract Ciphertext (from offset 16 to the end)
            val cipherTextLength = rawBytes.size - 16
            val cipherText = ByteArray(cipherTextLength)
            System.arraycopy(rawBytes, 16, cipherText, 0, cipherTextLength)

            // 4. Key derivation using PBKDF2WithHmacSHA256 (matches OpenSSL -pbkdf2)
            // OpenSSL requires 48 bytes total: 32 bytes for AES-256 Key, 16 bytes for IV
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keySpec: KeySpec = PBEKeySpec(
                ENCRYPTION_PASSPHRASE.toCharArray(),
                salt,
                PBKDF2_ITERATION_COUNT,
                DERIVED_KEY_AND_IV_LENGTH_BITS
            )
            val derivedBytes = secretKeyFactory.generateSecret(keySpec).encoded

            val aesKeyBytes = ByteArray(32)
            val ivBytes = ByteArray(16)
            System.arraycopy(derivedBytes, 0, aesKeyBytes, 0, 32)
            System.arraycopy(derivedBytes, 32, ivBytes, 0, 16)

            val secretKey = SecretKeySpec(aesKeyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)

            // 5. Decrypt in volatile RAM memory (never touches storage/disk)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decryptedDexBytes = cipher.doFinal(cipherText)

            // 6. Wrap into a direct ByteBuffer for InMemoryDexClassLoader
            val dexByteBuffer = ByteBuffer.wrap(decryptedDexBytes)

            // 7. Inject into Android ClassLoader parent chain via reflection
            val baseClassLoader = context.classLoader
            val parentField = ClassLoader::class.java.getDeclaredField("parent")
            parentField.isAccessible = true

            // Retrieve the original parent (usually BootClassLoader)
            val originalParent = parentField.get(baseClassLoader) as? ClassLoader

            // Instantiate InMemoryDexClassLoader pointing to the original parent
            val inMemoryDexClassLoader = InMemoryDexClassLoader(dexByteBuffer, originalParent)

            // Set InMemoryDexClassLoader as the new parent of the application's PathClassLoader
            parentField.set(baseClassLoader, inMemoryDexClassLoader)

        } catch (exception: Exception) {
            // Log or handle gracefully in case of internal failure
            exception.printStackTrace()
        }
    }

    companion object {
        private const val PAYLOAD_ASSET_NAME = "payload.bin"
        private const val OPENSSL_MAGIC_HEADER = "Salted__"
        private const val ENCRYPTION_PASSPHRASE = "FastGit_Master_Secret_2026"
        private const val PBKDF2_ITERATION_COUNT = 10000
        private const val DERIVED_KEY_AND_IV_LENGTH_BITS = 384 // (32 bytes AES Key + 16 bytes IV) * 8
    }
}