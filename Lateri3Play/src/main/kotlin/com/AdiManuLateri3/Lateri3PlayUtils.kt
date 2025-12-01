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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

// --- Extractor Helpers (FINAL FIX: Removed expression body, added IO scope for callback) ---

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
    size: String = ""
) {
    val fixSize = if(size.isNotEmpty()) " $size" else ""
    // FIX: Panggilan langsung di dalam suspend function, bukan di dalam = coroutineScope
    loadExtractor(url, referer, subtitleCallback) { link ->
        // FIX: Menggunakan CoroutineScope(Dispatchers.IO).launch agar aman dan sesuai StreamPlay
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

suspend fun loadCustomExtractor(
    name: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null
) {
    // FIX: Panggilan langsung di dalam suspend function
    loadExtractor(url, referer, subtitleCallback) { link ->
        // FIX: Menggunakan CoroutineScope(Dispatchers.IO).launch
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(name, name, link.url) {
                    this.type = link.type
                    this.referer = link.referer
                    this.quality = link.quality
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
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
        val cleanedUrl = url.replace("&#038;", "&")
        val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
        if (encodedLink.isEmpty()) return null
        val decodedUrl = base64Decode(encodedLink)
        
        // Logika tambahan dari StreamPlay untuk redirect
        val doc = app.get(decodedUrl).documentLarge
        val goValue = doc.select("form#landing input[name=go]").attr("value")
        if (goValue.isNotBlank()) {
             val decodedGoUrl = base64Decode(goValue).replace("&#038;", "&")
             val responseDoc = app.get(decodedGoUrl).documentLarge
             val script = responseDoc.select("script").firstOrNull { it.data().contains("window.location.replace") }?.data()
             val regex = Regex("""window\.location\.replace\s*\(\s*["'](.+?)["']\s*\)\s*;?""")
             val match = regex.find(script ?: "")
             val redirectPath = match?.groupValues?.get(1)
             if (redirectPath != null) {
                 return if (redirectPath.startsWith("http")) redirectPath else URI(decodedGoUrl).let { "${it.scheme}://${it.host}$redirectPath" }
             }
        }
        
        if (decodedUrl.startsWith("http")) decodedUrl else null
    } catch (e: Exception) { null }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun cinematickitloadBypass(url: String): String? {
    return try {
        val cleanedUrl = url.replace("&#038;", "&")
        val encodedLink = cleanedUrl.substringAfter("safelink=").substringBefore("-")
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
    
    val secretKey = if (useAltKey) {
        BuildConfig.MOVIEBOX_SECRET_KEY_ALT
    } else {
        BuildConfig.MOVIEBOX_SECRET_KEY_DEFAULT
    }
    
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

// FIX: Custom charset for VidFast
fun customEncode(input: ByteArray): String {
    val sourceChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    val targetChars = "7EkRi2WnMSlgLbXm_jy1vtO69ehrAV0-saUB5FGpoq3QuNIZ8wJ4PfdHxzTDKYCc"

    val base64 = Base64.encodeToString(
        input,
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )

    val translationMap = sourceChars.zip(targetChars).toMap()
    return base64.map { translationMap[it] ?: it }.joinToString("")
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
        val decoded = Base64.decode(encrypted, Base64.DEFAULT)
        val parts = String(decoded).split(":")
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
    return when (quality) {
        "4K", "2160P" -> Qualities.P2160.value
        "FHD", "1080P" -> Qualities.P1080.value
        "HQ", "HD", "720P", "DVDRIP" -> Qualities.P720.value
        "480P" -> Qualities.P480.value
        else -> Qualities.Unknown.value
    }
}

suspend fun getPlayer4uUrl(
    name: String,
    quality: Int,
    url: String,
    referer: String?,
    callback: (ExtractorLink) -> Unit
) {
    try {
        val response = app.get(url, referer = referer)
        val m3u8 = Regex("\"hls2\":\\s*\"(.*?m3u8.*?)\"").find(response.text)?.groupValues?.getOrNull(1)
            ?: return
            
        callback.invoke(newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
            this.quality = quality
        })
    } catch (e: Exception) {}
}

fun vidrockEncode(tmdb: String, type: String, season: Int? = null, episode: Int? = null): String {
    val base = if (type == "tv" && season != null && episode != null) {
        "$tmdb-$season-$episode"
    } else {
        tmdb
    }
    val first = Base64.encodeToString(base.reversed().toByteArray(), Base64.DEFAULT)
    return Base64.encodeToString(first.toByteArray(), Base64.DEFAULT)
}

suspend fun extractMdrive(url: String): List<String> {
    val regex = Regex("hubcloud|gdflix|gdlink", RegexOption.IGNORE_CASE)
    return try {
        app.get(url).documentLarge.select("a[href]").mapNotNull { 
            val href = it.attr("href")
            if(regex.containsMatchIn(href)) href else null 
        }
    } catch (e: Exception) { emptyList() }
}

fun extractMovieAPIlinks(s: String, m: String, api: String): String {
    return "" 
}

fun generateWpKey(r: String, m: String): String {
     val rList = r.split("\\x").toTypedArray()
    var n = ""
    // FIX: Menggunakan String(...) dengan ByteArray dari Base64.decode
    val decodedM = String(Base64.decode(m.split("").reversed().joinToString(""), Base64.DEFAULT))
    for (s in decodedM.split("|")) {
        val index = s.toIntOrNull()
        if (index != null) {
            n += "\\x" + rList[index + 1]
        }
    }
    return n
}

fun getLanguage(s: String): String {
    return when (s.lowercase()) {
        "en", "eng" -> "English"
        "id", "ind" -> "Indonesian"
        else -> s
    }
}
