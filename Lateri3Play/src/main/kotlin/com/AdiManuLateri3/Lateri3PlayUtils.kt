package com.AdiManuLateri3

import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode

fun getBaseUrl(url: String): String {
    return try {
        URI(url).let {
            "${it.scheme}://${it.host}"
        }
    } catch (e: Exception) {
        ""
    }
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return domain + url
        }
        return "$domain/$url"
    }
}

fun getQuality(str: String): Int {
    return when (str) {
        "360p" -> Qualities.P360.value
        "480p" -> Qualities.P480.value
        "720p" -> Qualities.P720.value
        "1080p" -> Qualities.P1080.value
        "1080p Ultra" -> Qualities.P1080.value
        "4K", "2160p" -> Qualities.P2160.value
        else -> getQualityFromName(str)
    }
}

// ================= CRYPTO UTILS (Diperlukan untuk RiveStream/PrimeWire jika ada enkripsi) =================

object CryptoAES {
    private const val KEY_SIZE = 32 // 256 bits
    private const val IV_SIZE = 16 // 128 bits
    private const val SALT_SIZE = 8 // 64 bits
    private const val HASH_CIPHER = "AES/CBC/PKCS7PADDING"
    private const val HASH_CIPHER_FALLBACK = "AES/CBC/PKCS5PADDING"
    private const val AES = "AES"

    fun decrypt(cipherText: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        return try {
            val cipherTextBytes = base64DecodeArray(cipherText)
            decryptAES(cipherTextBytes, keyBytes, ivBytes)
        } catch (e: Exception) {
            ""
        }
    }

    private fun decryptAES(
        cipherTextBytes: ByteArray,
        keyBytes: ByteArray,
        ivBytes: ByteArray
    ): String {
        return try {
            val cipher = try {
                Cipher.getInstance(HASH_CIPHER)
            } catch (e: Throwable) {
                Cipher.getInstance(HASH_CIPHER_FALLBACK)
            }
            val keyS = SecretKeySpec(keyBytes, AES)
            cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(ivBytes))
            cipher.doFinal(cipherTextBytes).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

object CryptoJS {
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 128
    private const val HASH_CIPHER = "AES/CBC/PKCS7Padding"
    private const val AES = "AES"
    private const val KDF_DIGEST = "MD5"

    fun decrypt(password: String, cipherText: String): String {
        val ctBytes = base64DecodeArray(cipherText)
        val saltBytes = Arrays.copyOfRange(ctBytes, 8, 16)
        val cipherTextBytes = Arrays.copyOfRange(ctBytes, 16, ctBytes.size)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        EvpKDF(password.toByteArray(), KEY_SIZE, IV_SIZE, saltBytes, key, iv)
        val cipher = Cipher.getInstance(HASH_CIPHER)
        val keyS = SecretKeySpec(key, AES)
        cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(iv))
        val plainText = cipher.doFinal(cipherTextBytes)
        return String(plainText)
    }

    private fun EvpKDF(
        password: ByteArray,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray,
        resultKey: ByteArray,
        resultIv: ByteArray
    ): ByteArray {
        return EvpKDF(password, keySize, ivSize, salt, 1, KDF_DIGEST, resultKey, resultIv)
    }

    private fun EvpKDF(
        password: ByteArray,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray,
        iterations: Int,
        hashAlgorithm: String,
        resultKey: ByteArray,
        resultIv: ByteArray
    ): ByteArray {
        val keySize = keySize / 32
        val ivSize = ivSize / 32
        val targetKeySize = keySize + ivSize
        val derivedBytes = ByteArray(targetKeySize * 4)
        var numberOfDerivedWords = 0
        var block: ByteArray? = null
        val hash = MessageDigest.getInstance(hashAlgorithm)
        while (numberOfDerivedWords < targetKeySize) {
            if (block != null) {
                hash.update(block)
            }
            hash.update(password)
            block = hash.digest(salt)
            hash.reset()
            for (i in 1 until iterations) {
                block = hash.digest(block!!)
                hash.reset()
            }
            System.arraycopy(
                block!!, 0, derivedBytes, numberOfDerivedWords * 4,
                min(block.size, (targetKeySize - numberOfDerivedWords) * 4)
            )
            numberOfDerivedWords += block.size / 4
        }
        System.arraycopy(derivedBytes, 0, resultKey, 0, keySize * 4)
        System.arraycopy(derivedBytes, keySize * 4, resultIv, 0, ivSize * 4)
        return derivedBytes
    }
}
