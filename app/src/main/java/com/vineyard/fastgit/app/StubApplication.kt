package com.vineyard.fastgit.app

import android.app.Application
import android.content.Context
import dalvik.system.BaseDexClassLoader
import dalvik.system.InMemoryDexClassLoader
import java.io.InputStream
import java.lang.reflect.Array as JavaArray
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
                return
            }

            val rawBytes: ByteArray = assetManager.open(PAYLOAD_ASSET_NAME).use { inputStream: InputStream ->
                inputStream.readBytes()
            }

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

            // 5. Decrypt in volatile RAM memory
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decryptedDexBytes = cipher.doFinal(cipherText)

            // 6. Wrap into a direct ByteBuffer
            val dexByteBuffer = ByteBuffer.wrap(decryptedDexBytes)

            // 7. Inject in-memory DEX elements directly into the application's PathClassLoader
            val baseClassLoader = context.classLoader as? BaseDexClassLoader ?: return
            val inMemoryDexClassLoader = InMemoryDexClassLoader(dexByteBuffer, baseClassLoader)

            val pathListField = BaseDexClassLoader::class.java.getDeclaredField("pathList").apply {
                isAccessible = true
            }
            val basePathList = pathListField.get(baseClassLoader) ?: return
            val inMemoryPathList = pathListField.get(inMemoryDexClassLoader) ?: return

            val dexElementsField = basePathList.javaClass.getDeclaredField("dexElements").apply {
                isAccessible = true
            }
            val baseElements = dexElementsField.get(basePathList) as? Array<*> ?: return
            val inMemoryElements = dexElementsField.get(inMemoryPathList) as? Array<*> ?: return

            val combinedElements = JavaArray.newInstance(
                baseElements.javaClass.componentType!!,
                baseElements.size + inMemoryElements.size
            )

            // Place inMemoryElements first so decrypted payload takes precedence
            System.arraycopy(inMemoryElements, 0, combinedElements, 0, inMemoryElements.size)
            System.arraycopy(baseElements, 0, combinedElements, inMemoryElements.size, baseElements.size)

            dexElementsField.set(basePathList, combinedElements)

        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }

    companion object {
        private const val PAYLOAD_ASSET_NAME = "payload.bin"
        private const val OPENSSL_MAGIC_HEADER = "Salted__"
        private const val ENCRYPTION_PASSPHRASE = "FastGit_Master_Secret_2026"
        private const val PBKDF2_ITERATION_COUNT = 10000
        private const val DERIVED_KEY_AND_IV_LENGTH_BITS = 384
    }
}