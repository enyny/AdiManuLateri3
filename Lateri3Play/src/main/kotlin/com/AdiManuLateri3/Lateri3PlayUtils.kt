package com.AdiManuLateri3

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.api.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

// ================= UTILITIES UMUM =================

// FUNGSI BARU MANUAL UNTUK MENGGANTIKAN YANG HILANG
fun base64UrlEncode(input: ByteArray): String {
    return android.util.Base64.encodeToString(input, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
}

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

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()
        ?.replace("\\s+".toRegex(), "-")
        ?.lowercase()
}

// Fungsi Retry untuk koneksi yang tidak stabil
suspend fun <T> retryIO(
    times: Int = 3,
    delayTime: Long = 1000,
    block: suspend () -> T
): T {
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(delayTime)
        }
    }
    return block() // percobaan terakhir, biarkan error jika gagal
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

suspend fun loadCustomExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    name ?: link.source,
                    name ?: link.name,
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

fun generateWpKey(r: String, m: String): String {
    val rList = r.split("\\x").toTypedArray()
    var n = ""
    val decodedM = String(base64Decode(m.split("").reversed().joinToString("")).toCharArray())
    for (s in decodedM.split("|")) {
        n += "\\x" + rList[Integer.parseInt(s) + 1]
    }
    return n
}

// ================= CRYPTO ENGINES (AES & Standard) =================

object CryptoJS {
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 128
    private const val HASH_CIPHER = "AES/CBC/PKCS7Padding"
    private const val AES = "AES"
    private const val KDF_DIGEST = "MD5"
    private const val APPEND = "Salted__"

    fun encrypt(password: String, plainText: String): String {
        val saltBytes = generateSalt(8)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        EvpKDF(password.toByteArray(), KEY_SIZE, IV_SIZE, saltBytes, key, iv)
        val keyS = SecretKeySpec(key, AES)
        val cipher = Cipher.getInstance(HASH_CIPHER)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keyS, ivSpec)
        val cipherText = cipher.doFinal(plainText.toByteArray())
        
        val sBytes = APPEND.toByteArray()
        val b = ByteArray(sBytes.size + saltBytes.size + cipherText.size)
        System.arraycopy(sBytes, 0, b, 0, sBytes.size)
        System.arraycopy(saltBytes, 0, b, sBytes.size, saltBytes.size)
        System.arraycopy(cipherText, 0, b, sBytes.size + saltBytes.size, cipherText.size)
        return base64Encode(b)
    }

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

    private fun generateSalt(length: Int): ByteArray {
        return ByteArray(length).apply {
            SecureRandom().nextBytes(this)
        }
    }
}

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

object CryptoAES {
    private const val KEY_SIZE = 32
    private const val IV_SIZE = 16
    private const val HASH_CIPHER = "AES/CBC/PKCS7PADDING"
    private const val AES = "AES"

    fun decrypt(cipherText: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        return try {
            val cipherTextBytes = base64DecodeArray(cipherText)
            val cipher = Cipher.getInstance(HASH_CIPHER)
            val keyS = SecretKeySpec(keyBytes, AES)
            cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(ivBytes))
            cipher.doFinal(cipherTextBytes).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

val languageMap: Map<String, Set<String>> = mapOf(
    "Afrikaans"   to setOf("af", "afr"),
    "Albanian"    to setOf("sq", "sqi", "alb"),
    "Amharic"     to setOf("am", "amh"),
    "Arabic"      to setOf("ar", "ara"),
    "Armenian"    to setOf("hy", "hye", "arm"),
    "Azerbaijani" to setOf("az", "aze"),
    "Basque"      to setOf("eu", "eus", "baq"),
    "Belarusian"  to setOf("be", "bel"),
    "Bengali"     to setOf("bn", "ben"),
    "Bosnian"     to setOf("bs", "bos"),
    "Bulgarian"   to setOf("bg", "bul"),
    "Catalan"     to setOf("ca", "cat"),
    "Chinese"     to setOf("zh", "zho", "chi"),
    "Croatian"    to setOf("hr", "hrv", "scr"),
    "Czech"       to setOf("cs", "ces", "cze"),
    "Danish"      to setOf("da", "dan"),
    "Dutch"       to setOf("nl", "nld", "dut"),
    "English"     to setOf("en", "eng"),
    "Estonian"    to setOf("et", "est"),
    "Filipino"    to setOf("tl", "tgl"),
    "Finnish"     to setOf("fi", "fin"),
    "French"      to setOf("fr", "fra", "fre"),
    "Galician"    to setOf("gl", "glg"),
    "Georgian"    to setOf("ka", "kat", "geo"),
    "German"      to setOf("de", "deu", "ger"),
    "Greek"       to setOf("el", "ell", "gre"),
    "Gujarati"    to setOf("gu", "guj"),
    "Hebrew"      to setOf("he", "heb"),
    "Hindi"       to setOf("hi", "hin"),
    "Hungarian"   to setOf("hu", "hun"),
    "Icelandic"   to setOf("is", "isl", "ice"),
    "Indonesian"  to setOf("id", "ind"),
    "Italian"     to setOf("it", "ita"),
    "Japanese"    to setOf("ja", "jpn"),
    "Kannada"     to setOf("kn", "kan"),
    "Kazakh"      to setOf("kk", "kaz"),
    "Korean"      to setOf("ko", "kor"),
    "Latvian"     to setOf("lv", "lav"),
    "Lithuanian"  to setOf("lt", "lit"),
    "Macedonian"  to setOf("mk", "mkd", "mac"),
    "Malay"       to setOf("ms", "msa", "may"),
    "Malayalam"   to setOf("ml", "mal"),
    "Maltese"     to setOf("mt", "mlt"),
    "Marathi"     to setOf("mr", "mar"),
    "Mongolian"   to setOf("mn", "mon"),
    "Nepali"      to setOf("ne", "nep"),
    "Norwegian"   to setOf("no", "nor"),
    "Persian"     to setOf("fa", "fas", "per"),
    "Polish"      to setOf("pl", "pol"),
    "Portuguese"  to setOf("pt", "por"),
    "Punjabi"     to setOf("pa", "pan"),
    "Romanian"    to setOf("ro", "ron", "rum"),
    "Russian"     to setOf("ru", "rus"),
    "Serbian"     to setOf("sr", "srp", "scc"),
    "Sinhala"     to setOf("si", "sin"),
    "Slovak"      to setOf("sk", "slk", "slo"),
    "Slovenian"   to setOf("sl", "slv"),
    "Spanish"     to setOf("es", "spa"),
    "Swahili"     to setOf("sw", "swa"),
    "Swedish"     to setOf("sv", "swe"),
    "Tamil"       to setOf("ta", "tam"),
    "Telugu"      to setOf("te", "tel"),
    "Thai"        to setOf("th", "tha"),
    "Turkish"     to setOf("tr", "tur"),
    "Ukrainian"   to setOf("uk", "ukr"),
    "Urdu"        to setOf("ur", "urd"),
    "Uzbek"       to setOf("uz", "uzb"),
    "Vietnamese"  to setOf("vi", "vie"),
    "Welsh"       to setOf("cy", "cym", "wel"),
    "Yiddish"     to setOf("yi", "yid")
)

fun getLanguage(code: String): String {
    val lower = code.lowercase()
    return languageMap.entries.firstOrNull { lower in it.value }?.key ?: "UnKnown"
}

// ================= NEW HELPERS =================

suspend fun getPlayer4uUrl(
    name: String,
    selectedQuality: Int,
    url: String,
    referer: String?,
    callback: (ExtractorLink) -> Unit
) {
    val response = app.get(url, referer = referer)
    var script = getAndUnpack(response.text).takeIf { it.isNotEmpty() }
        ?: response.document.selectFirst("script:containsData(sources:)")?.data()
    if (script == null) {
        val iframeUrl =
            Regex("""<iframe src="(.*?)"""").find(response.text)?.groupValues?.getOrNull(1)
                ?: return
        val iframeResponse = app.get(
            iframeUrl,
            referer = null,
            headers = mapOf("Accept-Language" to "en-US,en;q=0.5")
        )
        script = getAndUnpack(iframeResponse.text).takeIf { it.isNotEmpty() } ?: return
    }

    val m3u8 = Regex("\"hls2\":\\s*\"(.*?m3u8.*?)\"").find(script)?.groupValues?.getOrNull(1).orEmpty()
    callback(newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
        this.quality = selectedQuality
    })
}

fun getPlayer4UQuality(quality: String): Int {
    return when (quality) {
        "4K", "2160P" -> Qualities.P2160.value
        "FHD", "1080P" -> Qualities.P1080.value
        "HQ", "HD", "720P", "DVDRIP", "TVRIP", "HDTC", "PREDVD" -> Qualities.P720.value
        "480P" -> Qualities.P480.value
        "360P", "CAM" -> Qualities.P360.value
        "DS" -> Qualities.P144.value
        "SD" -> Qualities.P480.value
        "WEBRIP" -> Qualities.P720.value
        "BLURAY", "BRRIP" -> Qualities.P1080.value
        "HDRIP" -> Qualities.P1080.value
        "TS" -> Qualities.P480.value
        "R5" -> Qualities.P480.value
        "SCR" -> Qualities.P480.value
        "TC" -> Qualities.P480.value
        else -> Qualities.Unknown.value
    }
}

object AdiDewasaHelper {
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Connection" to "keep-alive",
        "Referer" to "https://dramafull.cc/"
    )

    fun normalizeQuery(title: String): String {
        return title
            .replace(Regex("\\(\\d{4}\\)"), "") 
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ") 
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    fun isFuzzyMatch(original: String, result: String): Boolean {
        val cleanOrg = original.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanRes = result.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (cleanOrg.length < 5 || cleanRes.length < 5) return cleanOrg == cleanRes
        return cleanOrg.contains(cleanRes) || cleanRes.contains(cleanOrg)
    }
}

object VidsrcHelper {
    fun encryptAesCbc(plainText: String, keyText: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(keyText.toByteArray(Charsets.UTF_8))
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val iv = ByteArray(16) { 0 }
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // MENGGUNAKAN FUNGSI MANUAL YANG KITA BUAT DI ATAS
        return base64UrlEncode(encrypted)
    }
}
