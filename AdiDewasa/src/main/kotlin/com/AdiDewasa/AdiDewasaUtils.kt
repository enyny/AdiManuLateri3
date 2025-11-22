package com.AdiDewasa

import android.util.Base64
import com.AdiDewasa.AdiDewasa.Companion.anilistAPI
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.isLowerCase

var gomoviesCookies: Map<String, String>? = null

val mimeType = arrayOf(
    "video/x-matroska",
    "video/mp4",
    "video/x-msvideo"
)

// ================= ADIDEWASA HELPER =================
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
        if (cleanOrg.length < 5 || cleanRes.length < 5) {
            return cleanOrg == cleanRes
        }
        return cleanOrg.contains(cleanRes) || cleanRes.contains(cleanOrg)
    }
}
// ====================================================

suspend fun tmdbToAnimeId(title: String?, year: Int?, season: String?, type: TvType): AniIds {
    val query = """
        query (
          ${'$'}page: Int = 1
          ${'$'}search: String
          ${'$'}sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]
          ${'$'}type: MediaType
          ${'$'}season: MediaSeason
          ${'$'}seasonYear: Int
          ${'$'}format: [MediaFormat]
        ) {
          Page(page: ${'$'}page, perPage: 20) {
            media(
              search: ${'$'}search
              sort: ${'$'}sort
              type: ${'$'}type
              season: ${'$'}season
              seasonYear: ${'$'}seasonYear
              format_in: ${'$'}format
            ) {
              id
              idMal
            }
          }
        }
    """.trimIndent().trim()

    val variables = mapOf(
        "search" to title,
        "sort" to "SEARCH_MATCH",
        "type" to "ANIME",
        "season" to season?.uppercase(),
        "seasonYear" to year,
        "format" to listOf(if (type == TvType.AnimeMovie) "MOVIE" else "TV")
    ).filterValues { value -> value != null && value.toString().isNotEmpty() }

    val data = mapOf(
        "query" to query,
        "variables" to variables
    ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

    val res = app.post(anilistAPI, requestBody = data)
        .parsedSafe<AniSearch>()?.data?.Page?.media?.firstOrNull()
    return AniIds(res?.id, res?.idMal)
}

fun generateWpKey(r: String, m: String): String {
    val rList = r.split("\\x").toTypedArray()
    var n = ""
    val decodedM = safeBase64Decode(m.reversed())
    for (s in decodedM.split("|")) {
        n += "\\x" + rList[Integer.parseInt(s) + 1]
    }
    return n
}

fun safeBase64Decode(input: String): String {
    var paddedInput = input
    val remainder = input.length % 4
    if (remainder != 0) {
        paddedInput += "=".repeat(4 - remainder)
    }
    return base64Decode(paddedInput)
}

fun getSeason(month: Int?): String? {
    val seasons = arrayOf("Winter", "Winter", "Spring", "Spring", "Spring", "Summer", "Summer", "Summer", "Fall", "Fall", "Fall", "Winter")
    if (month == null) return null
    return seasons[month - 1]
}

fun getEpisodeSlug(season: Int? = null, episode: Int? = null): Pair<String, String> {
    return if (season == null && episode == null) "" to "" else (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
}

fun getTitleSlug(title: String? = null): Pair<String?, String?> {
    val slug = title.createSlug()
    return slug?.replace("-", "\\W") to title?.replace(" ", "_")
}

fun getQuality(str: String): Int {
    return when (str) {
        "360p" -> Qualities.P240.value
        "480p" -> Qualities.P360.value
        "720p" -> Qualities.P480.value
        "1080p" -> Qualities.P720.value
        "1080p Ultra" -> Qualities.P1080.value
        else -> getQualityFromName(str)
    }
}

fun getLanguageNameFromCode(code: String?): String? {
    return code?.split("_")?.first()?.let { langCode ->
        try {
            Locale(langCode).displayLanguage.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } catch (e: Exception) {
            langCode
        }
    }
}

fun String.encodeUrl(): String {
    val url = URL(this)
    val uri = URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
    return uri.toURL().toString()
}

fun getBaseUrl(url: String): String {
    return URI(url).let { "${it.scheme}://${it.host}" }
}

fun String.fixUrlBloat(): String = this.replace("\"", "").replace("\\", "")

fun isUpcoming(dateString: String?): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (t: Throwable) {
        logError(t)
        false
    }
}

fun getDate(): TmdbDate {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calender = Calendar.getInstance()
    val today = formatter.format(calender.time)
    calender.add(Calendar.WEEK_OF_YEAR, 1)
    val nextWeek = formatter.format(calender.time)
    return TmdbDate(today, nextWeek)
}

fun decode(input: String): String = URLDecoder.decode(input, "utf-8")
fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) return url
    if (url.isEmpty()) return ""
    if (url.startsWith("//")) return "https:$url"
    return if (url.startsWith('/')) domain + url else "$domain/$url"
}

fun base64UrlEncode(input: ByteArray): String {
    return base64Encode(input).replace("+", "-").replace("/", "_").replace("=", "")
}

fun String.createSlug(): String? {
    return this.filter { it.isWhitespace() || it.isLetterOrDigit() }.trim().replace("\\s+".toRegex(), "-").lowercase()
}

fun String.xorDecrypt(key: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < this.length) {
        var j = 0
        while (j < key.length && i < this.length) {
            sb.append((this[i].code xor key[j].code).toChar())
            j++
            i++
        }
    }
    return sb.toString()
}

object VidrockHelper {
    private const val Ww = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9"
    fun encrypt(r: Int?, e: String, t: Int?, n: Int?): String {
        val s = if (e == "tv") "${r}_${t}_${n}" else r.toString()
        val keyBytes = Ww.toByteArray(Charsets.UTF_8)
        val ivBytes = Ww.substring(0, 16).toByteArray(Charsets.UTF_8)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return base64UrlEncode(cipher.doFinal(s.toByteArray(Charsets.UTF_8)))
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
        return base64UrlEncode(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)))
    }
}
