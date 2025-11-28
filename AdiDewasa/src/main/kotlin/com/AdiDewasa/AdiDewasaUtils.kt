package com.AdiDewasa

import android.util.Base64
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets // Import PENTING yang tadi kurang
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.text.isLowerCase

// Constants
const val OpenSubtitlesApiUrl = "https://opensubtitles-v3.strem.io"
const val WyZIESUBAPI = "https://sub.wyzie.ru"
const val tmdbAPI = "https://api.themoviedb.org/3"
const val anilistAPI = "https://graphql.anilist.co"
const val apiKey = "b030404650f279792a8d3287232358e3"
val mimeType = arrayOf("video/x-matroska", "video/mp4", "video/x-msvideo")

// ================= ADIDEWASA HELPER =================
object AdiDewasaHelper {
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
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

// ================= GENERAL UTILS =================

fun getLanguage(code: String): String {
    return try {
        Locale(code).displayLanguage
    } catch (e: Exception) {
        code
    }
}

fun getBaseUrl(url: String): String {
    return URI(url).let { "${it.scheme}://${it.host}" }
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) return url
    if (url.isEmpty()) return ""
    if (url.startsWith("//")) return "https:$url"
    return if (url.startsWith('/')) domain + url else "$domain/$url"
}

fun String.encodeUrl(): String {
    val url = URL(this)
    val uri = URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
    return uri.toURL().toString()
}

fun String?.createSlug(): String? {
    return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
        ?.trim()?.replace("\\s+".toRegex(), "-")?.lowercase()
}

fun getIndexQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}

fun getIndexQualityTags(str: String?): String {
    return Regex("(?i)\\d{3,4}[pP]\\.?(.*?)\\.(mkv|mp4|avi)").find(str ?: "")?.groupValues?.getOrNull(1)
        ?.replace(".", " ")?.trim() ?: str ?: ""
}

fun bytesToGigaBytes(number: Double): Double = number / 1024000000

fun getEpisodeSlug(season: Int?, episode: Int?): Pair<String, String> {
    return if (season == null && episode == null) "" to ""
    else (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
}

fun getTitleSlug(title: String?): Pair<String?, String?> {
    val slug = title.createSlug()
    return slug?.replace("-", "\\W") to title?.replace(" ", "_")
}

// ================= SUBTITLE HELPERS =================

suspend fun getImdbIdFromTitle(title: String, year: Int?, type: TvType): String? {
    try {
        val searchType = if (type == TvType.Movie) "movie" else "tv"
        val query = title.replace(" ", "+")
        val yearParam = if (year != null) {
            if (type == TvType.Movie) "&primary_release_year=$year" else "&first_air_date_year=$year"
        } else ""

        val searchUrl = "$tmdbAPI/search/$searchType?api_key=$apiKey&query=$query$yearParam"
        val searchRes = app.get(searchUrl).parsedSafe<TmdbSearchResponse>()
        val result = searchRes?.results?.firstOrNull() ?: return null

        val externalUrl = "$tmdbAPI/$searchType/${result.id}/external_ids?api_key=$apiKey"
        val externalRes = app.get(externalUrl).parsedSafe<TmdbExternalIds>()

        return externalRes?.imdb_id
    } catch (e: Exception) {
        return null
    }
}

suspend fun invokeSubtitleAPI(
    imdbId: String?,
    season: Int? = null,
    episode: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
) {
    if (imdbId == null) return
    val url = if (season == null) "$OpenSubtitlesApiUrl/subtitles/movie/$imdbId.json" 
              else "$OpenSubtitlesApiUrl/subtitles/series/$imdbId:$season:$episode.json"

    try {
        val res = app.get(url).parsedSafe<SubtitlesAPI>()
        res?.subtitles?.forEach { sub ->
            val langName = getLanguage(sub.lang).replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }
            subtitleCallback.invoke(SubtitleFile(langName, sub.url))
        }
    } catch (e: Exception) {}
}

suspend fun invokeWyZIESUBAPI(
    imdbId: String?,
    season: Int? = null,
    episode: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit
) {
    if (imdbId == null) return
    val url = buildString {
        append("$WyZIESUBAPI/search?id=$imdbId")
        if (season != null && episode != null) append("&season=$season&episode=$episode")
    }
    try {
        val res = app.get(url).text
        tryParseJson<List<WyZIESUB>>(res)?.forEach { sub ->
             subtitleCallback.invoke(SubtitleFile(sub.display, sub.url))
        }
    } catch (e: Exception) {}
}

// ================= INDEX SEARCH HELPERS =================

fun getIndexQuery(title: String?, year: Int?, season: Int?, episode: Int?): String {
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    return (if (season == null) "$title ${year ?: ""}" else "$title S${seasonSlug}E${episodeSlug}").trim()
}

fun searchIndex(
    title: String?,
    season: Int?,
    episode: Int?,
    year: Int?,
    response: String,
    isTrimmed: Boolean = true,
): List<IndexMedia>? {
    val files = tryParseJson<IndexSearch>(response)?.data?.files?.filter { media ->
        matchingIndex(media.name, media.mimeType, title, year, season, episode)
    }?.distinctBy { it.name }?.sortedByDescending { it.size?.toLongOrNull() ?: 0 } ?: return null

    return if (isTrimmed) {
        listOfNotNull(
            files.find { it.name?.contains("2160p", true) == true },
            files.find { it.name?.contains("1080p", true) == true }
        )
    } else files
}

fun matchingIndex(
    mediaName: String?,
    mediaMimeType: String?,
    title: String?,
    year: Int?,
    season: Int?,
    episode: Int?,
    include720: Boolean = false
): Boolean {
    if (mediaName == null) return false
    val (wSlug, dwSlug) = getTitleSlug(title)
    val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
    val matchesTitle = if (season == null) {
        mediaName.contains(Regex("(?i)(?:$wSlug|$dwSlug).*$year"))
    } else {
        mediaName.contains(Regex("(?i)(?:$wSlug|$dwSlug).*S${seasonSlug}.?E${episodeSlug}"))
    }
    val matchesQuality = mediaName.contains(
        if (include720) Regex("(?i)(2160p|1080p|720p)") else Regex("(?i)(2160p|1080p)")
    )
    val matchesType = (mediaMimeType in mimeType) || mediaName.contains(Regex("\\.mkv|\\.mp4|\\.avi"))
    return matchesTitle && matchesQuality && matchesType
}

fun decodeIndexJson(json: String): String {
    val slug = json.reversed().substring(24)
    return String(android.util.Base64.decode(slug.substring(0, slug.length - 20), android.util.Base64.DEFAULT))
}

// ================= CINEMAOS HELPERS =================

fun generateHashedString(): String {
    val s = "a8f7e9c2d4b6a1f3e8c9d2t4a7f6e9c2d4z6a1f3e8c9d2b4a7f5e9c2d4b6a1f3"
    val algorithm = "HmacSHA512"
    val keySpec = SecretKeySpec(s.toByteArray(StandardCharsets.UTF_8), algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(keySpec)
    val input = "crypto_rotation_v2_seed_2025"
    val hmacBytes = mac.doFinal(input.toByteArray(StandardCharsets.UTF_8))
    val hex = hmacBytes.joinToString("") { "%02x".format(it) }
    val repeated = hex.repeat(3)
    return repeated.substring(0, max(s.length, 128))
}

fun hexStringToByteArray(hex: String): ByteArray {
    val len = hex.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

// ================= ANIME HELPERS (FIXED) =================

fun getSeason(month: Int?): String? {
    val seasons = arrayOf(
        "Winter", "Winter", "Spring", "Spring", "Spring", "Summer",
        "Summer", "Summer", "Fall", "Fall", "Fall", "Winter"
    )
    if (month == null) return null
    return seasons[month - 1]
}

suspend fun convertTmdbToAnimeId(title: String?, date: String?, airedDate: String?, type: TvType): AniIds {
    val sDate = date?.split("-")
    val sAiredDate = airedDate?.split("-")
    val year = sDate?.firstOrNull()?.toIntOrNull()
    val airedYear = sAiredDate?.firstOrNull()?.toIntOrNull()
    val season = getSeason(sDate?.get(1)?.toIntOrNull())
    val airedSeason = getSeason(sAiredDate?.get(1)?.toIntOrNull())

    return if (type == TvType.AnimeMovie) {
        tmdbToAnimeId(title, airedYear, "", type)
    } else {
        val ids = tmdbToAnimeId(title, year, season, type)
        if (ids.id == null && ids.idMal == null) tmdbToAnimeId(title, airedYear, airedSeason, type) else ids
    }
}

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
