package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Base64
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import androidx.core.net.toUri
import org.json.JSONArray
import com.AdiManuLateri3.BuildConfig 

// --- General String Utils ---

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()
}

fun cleanTitle(title: String): String {
    return title.replace(Regex("(?i)(season|episode|complete|uncensored).*"), "").trim()
}

fun getBaseUrl(url: String): String {
    return try {
        URI(url).let { "${it.scheme}://${it.host}" }
    } catch (e: Exception) { "" }
}

fun String.getHost(): String {
    return try { URI(this).host } catch (e: Exception) { "" }
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) return url
    if (url.isEmpty()) return ""
    if (url.startsWith("//")) return "https:$url"
    return if (url.startsWith('/')) "$domain$url" else "$domain/$url"
}

fun httpsify(url: String): String {
    if (url.startsWith("//")) return "https:$url"
    return url
}

// --- Async Helpers ---

suspend fun runLimitedAsync(
    concurrency: Int = 5,
    vararg tasks: suspend () -> Unit
) = coroutineScope {
    val semaphore = Semaphore(concurrency)
    tasks.map { task ->
        async(Dispatchers.IO) {
            semaphore.withPermit {
                try {
                    task()
                } catch (e: Exception) {
                    // Log error but continue
                }
            }
        }
    }.awaitAll()
}

// --- Image & Date Utils ---

fun getDate(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(Date())
}

fun isUpcoming(dateString: String?): Boolean {
    if (dateString.isNullOrEmpty()) return false
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateString)
        date != null && date.after(Date())
    } catch (e: Exception) { false }
}

// --- Extractor Helpers (FIXED with coroutineScope) ---

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null
) = coroutineScope { // FIX: Menggunakan coroutineScope body
    loadExtractor(url, referer, subtitleCallback) { link ->
        callback.invoke(
            newExtractorLink(
                "$source [${link.source}]",
                "$source [${link.source}]",
                link.url,
            ) {
                this.quality = quality ?: link.quality
                this.type = link.type
                this.referer = link.referer
                this.headers = link.headers
            }
        )
    }
}

suspend fun loadCustomExtractor(
    name: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) = coroutineScope { // FIX: Menggunakan coroutineScope body
    loadExtractor(url, referer, subtitleCallback) { link ->
        callback.invoke(
            newExtractorLink(name, name, link.url) {
                this.type = link.type
                this.referer = link.referer
                this.quality = link.quality
            }
        )
    }
}

suspend fun dispatchToExtractor(
    link: String,
    source: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    loadSourceNameExtractor(source, link, "", subtitleCallback, callback)
}

// --- Bypass Logic ---

suspend fun bypassHrefli(url: String): String? {
    return try {
        val host = getBaseUrl(url)
        var res = app.get(url).document
        val formUrl = res.select("form#landing").attr("action")
        val formData = res.select("form#landing input").associate { it.attr("name") to it.attr("value") }
        
        res = app.post(formUrl, data = formData).document
        val skToken = res.selectFirst("script:containsData(?go=)")?.data()?.substringAfter("?go=")?.substringBefore("\"") ?: return null
        
        val driveUrl = app.get("$host?go=$skToken", cookies = mapOf(skToken to "${formData["_wp_http2"]}")).document
            .selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
            
        driveUrl
    } catch (e: Exception) { null }
}

suspend fun cinematickitBypass(url: String): String? {
    return try {
        val encodedLink = url.substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val decodedUrl = base64Decode(encodedLink)
        if (decodedUrl.startsWith("http")) decodedUrl else null
    } catch (e: Exception) { null }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitloadBypass(url: String): String? {
    return try {
        val encodedLink = url.substringAfter("safelink=").substringBefore("-")
        String(Base64.decode(encodedLink, Base64.DEFAULT))
    } catch (e: Exception) { null }
}

// --- MovieBox & VidFast Utils ---

private fun md5(input: ByteArray): String {
    return MessageDigest.getInstance("MD5").digest(input)
        .joinToString("") { "%02x".format(it) }
}

private fun reverseString(input: String): String = input.reversed()

fun generateXClientToken(hardcodedTimestamp: Long? = null): String {
    val timestamp = (hardcodedTimestamp ?: System.currentTimeMillis()).toString()
    val reversed = reverseString(timestamp)
    val hash = md5(reversed.toByteArray())
    return "$timestamp,$hash"
}

fun generateXTrSignature(
    method: String,
    accept: String? = "application/json",
    contentType: String? = "application/json",
    url: String,
    body: String? = null,
    useAltKey: Boolean = false,
    hardcodedTimestamp: Long? = null
): String {
    val timestamp = hardcodedTimestamp ?: System.currentTimeMillis()

    val canonical = buildCanonicalString(
        method = method,
        accept = accept,
        contentType = contentType,
        url = url,
        body = body,
        timestamp = timestamp
    )
    
    // PENTING: Pastikan Keys ini diisi di build.gradle.kts
    val secretKey = if (useAltKey) {
        BuildConfig.MOVIEBOX_SECRET_KEY_ALT
    } else {
        BuildConfig.MOVIEBOX_SECRET_KEY_DEFAULT
    }
    
    // Cegah crash jika key masih placeholder
    if (secretKey.contains("PlaceHolder")) return ""

    val secretBytes = Base64.decode(secretKey, Base64.DEFAULT)
    val mac = Mac.getInstance("HmacMD5").apply {
        init(SecretKeySpec(secretBytes, "HmacMD5"))
    }
    val rawSignature = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
    val signatureBase64 = Base64.encodeToString(rawSignature, Base64.NO_WRAP)
    return "$timestamp|2|$signatureBase64"
}

private fun buildCanonicalString(
    method: String,
    accept: String?,
    contentType: String?,
    url: String,
    body: String?,
    timestamp: Long
): String {
    val parsed = url.toUri()
    val path = parsed.path ?: ""
    val query = if (parsed.queryParameterNames.isNotEmpty()) {
        parsed.queryParameterNames.sorted().joinToString("&") { key ->
            parsed.getQueryParameters(key).joinToString("&") { value ->
                "$key=$value" 
            }
        }
    } else ""

    val canonicalUrl = if (query.isNotEmpty()) "$path?$query" else path
    val bodyBytes = body?.toByteArray(Charsets.UTF_8)
    val bodyHash = if (bodyBytes != null) {
        val trimmed = if (bodyBytes.size > 102400) bodyBytes.copyOfRange(0, 102400) else bodyBytes
        md5(trimmed)
    } else ""

    val bodyLength = bodyBytes?.size?.toString() ?: ""
    return "${method.uppercase()}\n" +
            "${accept ?: ""}\n" +
            "${contentType ?: ""}\n" +
            "$bodyLength\n" +
            "$timestamp\n" +
            "$bodyHash\n" +
            canonicalUrl
}

// --- VidFast Utils ---

fun hexStringToByteArray2(hex: String): ByteArray {
    val result = ByteArray(hex.length / 2)
    for (i in hex.indices step 2) {
        val value = hex.substring(i, i + 2).toInt(16)
        result[i / 2] = value.toByte()
    }
    return result
}

fun padData(data: ByteArray, blockSize: Int): ByteArray {
    val padding = blockSize - (data.size % blockSize)
    val result = ByteArray(data.size + padding)
    System.arraycopy(data, 0, result, 0, data.size)
    for (i in data.size until result.size) {
        result[i] = padding.toByte()
    }
    return result
}

fun customEncode(input: ByteArray): String {
    val sourceChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    val targetChars = "4stjqN6BT05-L8rQe_HxWmAVv9icYKaCDzIP1fZ7kwXRyFhd2GEng3SMJlUubOop"

    val translationMap = sourceChars.zip(targetChars).toMap()
    val encoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input)
    } else {
        Base64.encodeToString(input, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    return encoded.map { char ->
        translationMap[char] ?: char
    }.joinToString("")
}

fun parseServers(jsonString: String): List<VidFastServer> {
    val servers = mutableListOf<VidFastServer>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val server = VidFastServer(
                name = jsonObject.getString("name"),
                description = jsonObject.getString("description"),
                image = jsonObject.getString("image"),
                data = jsonObject.getString("data")
            )
            servers.add(server)
        }
    } catch (e: Exception) {
        Log.e("Lateri3Play", "Manual parsing failed: ${e.message}")
    }
    return servers
}

// --- Decryption & Keys ---

fun decryptVidzeeUrl(encrypted: String, key: ByteArray): String {
    return try {
        val decoded = base64Decode(encrypted)
        val parts = decoded.split(":")
        val iv = Base64.decode(parts[0], Base64.DEFAULT)
        val cipherData = Base64.decode(parts[1], Base64.DEFAULT)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

        String(cipher.doFinal(cipherData))
    } catch (e: Exception) { "" }
}

@RequiresApi(Build.VERSION_CODES.O)
fun generateVrfAES(movieId: String, userId: String): String {
    val keyData = "hack_$userId".toByteArray(Charsets.UTF_8)
    val keyBytes = MessageDigest.getInstance("SHA-256").digest(keyData)
    val keySpec = SecretKeySpec(keyBytes, "AES")
    val ivSpec = IvParameterSpec(ByteArray(16))
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    val encrypted = cipher.doFinal(movieId.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(encrypted, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

suspend fun hdhubgetRedirectLinks(url: String): String {
    return try {
        val doc = app.get(url).text
        url 
    } catch (e: Exception) { "" }
}

fun getKisskhTitle(str: String?): String? {
    return str?.replace(Regex("[^a-zA-Z\\d]"), "-")
}

fun getPlayer4UQuality(quality: String): Int {
    return Qualities.Unknown.value
}

suspend fun getPlayer4uUrl(
    name: String,
    quality: Int,
    url: String,
    referer: String,
    callback: (ExtractorLink) -> Unit
) {
    try {
        val res = app.get(url, referer = referer).text
        val m3u8 = Regex("file\":\"(.*?)\"").find(res)?.groupValues?.get(1)
        if (m3u8 != null) {
            callback.invoke(newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                this.quality = quality
            })
        }
    } catch (e: Exception) {}
}

fun vidrockEncode(tmdb: String, type: String, season: Int? = null, episode: Int? = null): String {
    val base = if (type == "tv" && season != null && episode != null) "$tmdb-$season-$episode" else tmdb
    val first = Base64.encode(base.reversed().toByteArray(), Base64.DEFAULT)
    return Base64.encodeToString(first, Base64.DEFAULT)
}

suspend fun extractMdrive(url: String): List<String> {
    return try {
        app.get(url).document.select("a[href]").map { it.attr("href") }
            .filter { it.contains("hubcloud") || it.contains("gdflix") }
    } catch (e: Exception) { emptyList() }
}

// --- Stubs ---
fun extractIframeUrl(url: String): String? = null
fun extractProrcpUrl(url: String): String? = null
fun extractAndDecryptSource(url: String, ref: String): List<Any> = emptyList()
fun extractMovieAPIlinks(s: String, m: String, api: String): String = ""
fun generateWpKey(r: String, m: String): String = ""
fun getLanguage(s: String): String = "English"
