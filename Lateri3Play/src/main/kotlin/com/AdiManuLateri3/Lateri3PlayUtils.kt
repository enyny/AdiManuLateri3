package com.AdiManuLateri3

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.api.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

// ================= UTILITIES UTAMA =================

fun getBaseUrl(url: String): String {
    return try {
        URI(url).let { "${it.scheme}://${it.host}" }
    } catch (e: Exception) {
        ""
    }
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) return url
    if (url.isEmpty()) return ""
    if (url.startsWith("//")) return "https:$url"
    return if (url.startsWith('/')) "$domain$url" else "$domain/$url"
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

fun String.createSlug(): String? {
    return this.filter { it.isWhitespace() || it.isLetterOrDigit() }
        .trim()
        .replace("\\s+".toRegex(), "-")
        .lowercase()
}

// Helper untuk memuat Extractor dengan nama custom
suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
    size: String = ""
) {
    val fixSize = if (size.isNotEmpty()) " $size" else ""
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    "$source[${link.source}$fixSize]",
                    "$source[${link.source}$fixSize]",
                    link.url,
                ) {
                    this.quality = quality ?: link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}

// ================= BYPASS & SCRAPERS KHUSUS =================

// Bypass Href.li / UnblockedGames (Sering dipakai UHDMovies/VegaMovies)
suspend fun bypassHrefli(url: String): String? {
    fun Document.getFormUrl(): String = this.select("form#landing").attr("action")
    fun Document.getFormData(): Map<String, String> =
        this.select("form#landing input").associate { it.attr("name") to it.attr("value") }

    val host = getBaseUrl(url)
    var res = app.get(url).documentLarge
    var formUrl = res.getFormUrl()
    var formData = res.getFormData()

    // Step 1
    res = app.post(formUrl, data = formData).documentLarge
    formUrl = res.getFormUrl()
    formData = res.getFormData()

    // Step 2
    res = app.post(formUrl, data = formData).documentLarge
    val skToken = res.selectFirst("script:containsData(?go=)")?.data()
        ?.substringAfter("?go=")?.substringBefore("\"") ?: return null
    
    val driveUrl = app.get(
        "$host?go=$skToken", 
        cookies = mapOf(skToken to "${formData["_wp_http2"]}")
    ).documentLarge.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")

    val path = app.get(driveUrl ?: return null).text
        .substringAfter("replace(\"").substringBefore("\")")
    
    if (path == "/404") return null
    return fixUrl(path, getBaseUrl(driveUrl))
}

// HDHub4u Redirect Resolver
suspend fun hdhubgetRedirectLinks(url: String): String {
    val doc = app.get(url).text
    val regex = "s\\('o','([A-Za-z0-9+/=]+)'|ck\\('_wp_http_\\d+','([^']+)'".toRegex()
    val combinedString = buildString {
        regex.findAll(doc).forEach { matchResult ->
            val extractedValue = matchResult.groups[1]?.value ?: matchResult.groups[2]?.value
            if (!extractedValue.isNullOrEmpty()) append(extractedValue)
        }
    }
    return try {
        val decodedString = base64Decode(hdhubpen(base64Decode(base64Decode(combinedString))))
        val jsonObject = JSONObject(decodedString)
        val encodedurl = base64Decode(jsonObject.optString("o", "")).trim()
        val data = hdhubencode(jsonObject.optString("data", "")).trim()
        val wphttp1 = jsonObject.optString("blog_url", "").trim()
        val directlink = runCatching {
            app.get("$wphttp1?re=$data".trim()).documentLarge.select("body").text().trim()
        }.getOrDefault("").trim()

        encodedurl.ifEmpty { directlink }
    } catch (e: Exception) {
        "" 
    }
}

fun hdhubencode(encoded: String): String {
    return String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
}

fun hdhubpen(value: String): String {
    return value.map {
        when (it) {
            in 'A'..'Z' -> ((it - 'A' + 13) % 26 + 'A'.code).toChar()
            in 'a'..'z' -> ((it - 'a' + 13) % 26 + 'a'.code).toChar()
            else -> it
        }
    }.joinToString("")
}

// MoviesDrive Link Extractor
suspend fun extractMdrive(url: String): List<String> {
    val regex = Regex("hubcloud|gdflix|gdlink", RegexOption.IGNORE_CASE)
    return try {
        app.get(url).documentLarge
            .select("a[href]")
            .mapNotNull { element ->
                val href = element.attr("href")
                if (regex.containsMatchIn(href)) href else null
            }
    } catch (e: Exception) {
        emptyList()
    }
}

// ================= CRYPTO UTILS (AES) =================

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
        password: ByteArray, keySize: Int, ivSize: Int, salt: ByteArray,
        resultKey: ByteArray, resultIv: ByteArray
    ): ByteArray {
        val targetKeySize = (keySize / 32) + (ivSize / 32)
        val derivedBytes = ByteArray(targetKeySize * 4)
        var numberOfDerivedWords = 0
        var block: ByteArray? = null
        val hash = MessageDigest.getInstance(KDF_DIGEST)
        while (numberOfDerivedWords < targetKeySize) {
            if (block != null) hash.update(block)
            hash.update(password)
            block = hash.digest(salt)
            hash.reset()
            System.arraycopy(block!!, 0, derivedBytes, numberOfDerivedWords * 4, min(block.size, (targetKeySize - numberOfDerivedWords) * 4))
            numberOfDerivedWords += block.size / 4
        }
        System.arraycopy(derivedBytes, 0, resultKey, 0, keySize / 8)
        System.arraycopy(derivedBytes, keySize / 8, resultIv, 0, ivSize / 8)
        return derivedBytes
    }
}
