package com.vineyard.fastgit.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import dalvik.system.InMemoryDexClassLoader
import java.io.InputStream
import java.lang.reflect.Array
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class StubApplication : Application() {

    private var customClassLoader: ClassLoader? = null

    override fun getClassLoader(): ClassLoader {
        return customClassLoader ?: super.getClassLoader()
    }

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

            // Verify OpenSSL magic header ("Salted__")
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

            // 7. Instantiate PayloadClassLoader with context.classLoader as parent
            val baseClassLoader = context.classLoader
            val payloadLoader = PayloadClassLoader(dexByteBuffer, baseClassLoader)
            this.customClassLoader = payloadLoader

            // 8. Safely inject into Android's LoadedApk, ContextImpl, and system PathList dexElements
            replaceApplicationClassLoader(context, payloadLoader)
            payloadLoader.injectIntoPathList(baseClassLoader)

        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }

    private fun replaceApplicationClassLoader(context: Context, newClassLoader: ClassLoader) {
        try {
            Thread.currentThread().contextClassLoader = newClassLoader

            var currentContext: Any? = context
            while (currentContext is ContextWrapper) {
                currentContext = currentContext.baseContext
            }

            if (currentContext != null) {
                val contextImplClass = currentContext.javaClass
                setFieldValue(contextImplClass, currentContext, "mClassLoader", newClassLoader)

                val loadedApk = getFieldValue(contextImplClass, currentContext, "mPackageInfo")
                if (loadedApk != null) {
                    setFieldValue(loadedApk.javaClass, loadedApk, "mClassLoader", newClassLoader)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setFieldValue(clazz: Class<*>, target: Any, fieldName: String, value: Any?) {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
    }

    private fun getFieldValue(clazz: Class<*>, target: Any, fieldName: String): Any? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    /**
     * Non-final ClassLoader delegating payload resolution to an internal
     * InMemoryDexClassLoader while keeping APK PathClassLoader as fallback parent.
     */
    private class PayloadClassLoader(
        dexBuffer: ByteBuffer,
        parent: ClassLoader
    ) : ClassLoader(parent) {

        private val inMemoryLoader = InMemoryDexClassLoader(dexBuffer, parent)
        private val findClassMethod: Method = ClassLoader::class.java.getDeclaredMethod("findClass", String::class.java).apply {
            isAccessible = true
        }

        fun injectIntoPathList(pathClassLoader: ClassLoader) {
            try {
                val systemPathList = getField(pathClassLoader.javaClass, pathClassLoader as Any, "pathList") ?: return
                val payloadPathList = getField(inMemoryLoader.javaClass, inMemoryLoader as Any, "pathList") ?: return

                val systemElements = getField(systemPathList.javaClass, systemPathList, "dexElements") ?: return
                val payloadElements = getField(payloadPathList.javaClass, payloadPathList, "dexElements") ?: return

                val componentType = systemElements.javaClass.componentType ?: return
                val systemLength = Array.getLength(systemElements)
                val payloadLength = Array.getLength(payloadElements)

                val mergedElements = Array.newInstance(componentType, systemLength + payloadLength)
                for (i in 0 until systemLength) {
                    Array.set(mergedElements, i, Array.get(systemElements, i))
                }
                for (i in 0 until payloadLength) {
                    Array.set(mergedElements, systemLength + i, Array.get(payloadElements, i))
                }

                setField(systemPathList.javaClass, systemPathList, "dexElements", mergedElements)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun getField(clazz: Class<*>, target: Any, fieldName: String): Any? {
            var current: Class<*>? = clazz
            while (current != null) {
                try {
                    val field = current.getDeclaredField(fieldName)
                    field.isAccessible = true
                    return field.get(target)
                } catch (e: NoSuchFieldException) {
                    current = current.superclass
                }
            }
            return null
        }

        private fun setField(clazz: Class<*>, target: Any, fieldName: String, value: Any?) {
            var current: Class<*>? = clazz
            while (current != null) {
                try {
                    val field = current.getDeclaredField(fieldName)
                    field.isAccessible = true
                    field.set(target, value)
                    return
                } catch (e: NoSuchFieldException) {
                    current = current.superclass
                }
            }
        }

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            // Android OS and standard Java platform classes
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")) {
                return super.loadClass(name, resolve)
            }

            // Return cached if already resolved
            val loaded = findLoadedClass(name)
            if (loaded != null) {
                return loaded
            }

            // Load encrypted payload classes first
            return try {
                val clazz = findClassMethod.invoke(inMemoryLoader, name) as Class<*>
                if (resolve) {
                    resolveClass(clazz)
                }
                clazz
            } catch (e: Exception) {
                // Fall back to APK PathClassLoader (Kotlin stdlib, AndroidX, Compose, etc.)
                super.loadClass(name, resolve)
            }
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