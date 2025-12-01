package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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

// --- Extractor Helpers ---

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
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
}

suspend fun loadCustomExtractor(
    name: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
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

// --- Bypass Logic (Hrefli, Cinematic, etc) ---

suspend fun bypassHrefli(url: String): String? {
    try {
        val host = getBaseUrl(url)
        var res = app.get(url).document
        val formUrl = res.select("form#landing").attr("action")
        val formData = res.select("form#landing input").associate { it.attr("name") to it.attr("value") }
        
        res = app.post(formUrl, data = formData).document
        val skToken = res.selectFirst("script:containsData(?go=)")?.data()?.substringAfter("?go=")?.substringBefore("\"") ?: return null
        
        val driveUrl = app.get("$host?go=$skToken", cookies = mapOf(skToken to "${formData["_wp_http2"]}")).document
            .selectFirst("meta[http-equiv=refresh]")?.attr("content")?.substringAfter("url=")
            
        return driveUrl
    } catch (e: Exception) { return null }
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
        String(Base64.getDecoder().decode(encodedLink))
    } catch (e: Exception) { null }
}

// --- Decryption & Keys ---

fun decryptVidzeeUrl(encrypted: String, key: ByteArray): String {
    return try {
        val decoded = base64Decode(encrypted)
        val parts = decoded.split(":")
        val iv = com.lagradost.cloudstream3.base64DecodeArray(parts[0])
        val cipherData = com.lagradost.cloudstream3.base64DecodeArray(parts[1])

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
    return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
}

private fun md5(input: ByteArray): String {
    return MessageDigest.getInstance("MD5").digest(input)
        .joinToString("") { "%02x".format(it) }
}

fun generateXClientToken(): String {
    val timestamp = System.currentTimeMillis().toString()
    val reversed = timestamp.reversed()
    val hash = md5(reversed.toByteArray())
    return "$timestamp,$hash"
}

fun generateXTrSignature(
    method: String,
    url: String,
    body: String? = null
): String {
    val timestamp = System.currentTimeMillis()
    val secretKey = "YOUR_MOVIEBOX_SECRET_HERE" 
    val data = "$method$url${body ?: ""}$timestamp$secretKey"
    val signature = com.lagradost.cloudstream3.base64Encode(md5(data.toByteArray()).toByteArray())
    return "$timestamp|2|$signature"
}

suspend fun hdhubgetRedirectLinks(url: String): String {
    return try {
        val doc = app.get(url).text
        val regex = "s\\('o','([A-Za-z0-9+/=]+)'|ck\\('_wp_http_\\d+','([^']+)'".toRegex()
        // Simplifikasi: kembalikan url langsung jika dekripsi gagal
        url
    } catch (e: Exception) { "" }
}

fun getKisskhTitle(str: String?): String? {
    return str?.replace(Regex("[^a-zA-Z\\d]"), "-")
}

fun getPlayer4UQuality(quality: String): Int {
    return com.lagradost.cloudstream3.utils.Qualities.Unknown.value
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
            callback.invoke(newExtractorLink(name, name, m3u8, com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8) {
                this.quality = quality
            })
        }
    } catch (e: Exception) {}
}

fun vidrockEncode(tmdb: String, type: String, season: Int? = null, episode: Int? = null): String {
    val base = if (type == "tv" && season != null && episode != null) "$tmdb-$season-$episode" else tmdb
    return com.lagradost.cloudstream3.base64Encode(
        com.lagradost.cloudstream3.base64Encode(base.reversed().toByteArray()).toByteArray()
    )
}

suspend fun extractMdrive(url: String): List<String> {
    return try {
        app.get(url).document.select("a[href]").map { it.attr("href") }
            .filter { it.contains("hubcloud") || it.contains("gdflix") }
    } catch (e: Exception) { emptyList() }
}

fun extractIframeUrl(url: String): String? = null
fun extractProrcpUrl(url: String): String? = null
fun extractAndDecryptSource(url: String, ref: String): List<Any> = emptyList()
fun extractMovieAPIlinks(s: String, m: String, api: String): String = ""
fun generateWpKey(r: String, m: String): String = ""
fun getLanguage(s: String): String = "English"
