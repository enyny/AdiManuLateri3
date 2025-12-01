package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Base64
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
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// --- Data Classes ---
data class DomainsParser(
    val moviesdrive: String?,
    val hdhub4u: String?,
    val n4khdhub: String?,
    val multiMovies: String?,
    val bollyflix: String?,
    val uhdmovies: String?,
    val moviesmod: String?,
    val topMovies: String?,
    val hdmovie2: String?,
    val vegamovies: String?,
    val rogmovies: String?,
    val luxmovies: String?,
    val xprime: String?,
    val extramovies: String?,
    val dramadrip: String?,
    val toonstream: String?,
)

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

// --- Extractor Helpers (FIXED: suspend added, removed extra launch) ---

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null
) {
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
        String(Base64.decode(encodedLink, Base64.DEFAULT))
    } catch (e: Exception) { null }
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
    val signature = Base64.encodeToString(md5(data.toByteArray()).toByteArray(), Base64.NO_WRAP)
    return "$timestamp|2|$signature"
}

suspend fun hdhubgetRedirectLinks(url: String): String {
    return try {
        val doc = app.get(url).text
        url // Simplified return for now
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

fun extractIframeUrl(url: String): String? = null
fun extractProrcpUrl(url: String): String? = null
fun extractAndDecryptSource(url: String, ref: String): List<Any> = emptyList()
fun extractMovieAPIlinks(s: String, m: String, api: String): String = ""
fun generateWpKey(r: String, m: String): String = ""
fun getLanguage(s: String): String = "English"
