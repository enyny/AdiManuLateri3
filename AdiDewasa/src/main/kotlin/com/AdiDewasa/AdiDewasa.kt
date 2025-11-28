package com.AdiDewasa

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.util.Locale

// Constants
const val OpenSubtitlesApiUrl = "https://opensubtitles-v3.strem.io"
const val WyZIESUBAPI = "https://sub.wyzie.ru"
const val tmdbAPI = "https://api.themoviedb.org/3"
const val apiKey = "b030404650f279792a8d3287232358e3" 

// ================= ADIDEWASA HELPER =================
object AdiDewasaHelper {
    // Header statis
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Connection" to "keep-alive",
        "Referer" to "https://dramafull.cc/"
    )

    // Fungsi normalisasi judul
    fun normalizeQuery(title: String): String {
        return title
            .replace(Regex("\\(\\d{4}\\)"), "") 
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ") 
            .trim()
            .replace("\\s+".toRegex(), " ") 
    }

    // Fungsi Fuzzy Match
    fun isFuzzyMatch(original: String, result: String): Boolean {
        val cleanOrg = original.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanRes = result.lowercase().replace(Regex("[^a-z0-9]"), "")

        if (cleanOrg.length < 5 || cleanRes.length < 5) {
            return cleanOrg == cleanRes
        }
        return cleanOrg.contains(cleanRes) || cleanRes.contains(cleanOrg)
    }
}

// Helper Bahasa
fun getLanguage(code: String): String {
    return try {
        Locale(code).displayLanguage
    } catch (e: Exception) {
        code
    }
}

// Helper: Cari IMDB ID via TMDB
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
        e.printStackTrace()
        return null
    }
}

// API: OpenSubtitles
suspend fun invokeSubtitleAPI(
    imdbId: String?,
    season: Int? = null,
    episode: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
) {
    if (imdbId == null) return
    
    val url = if (season == null) {
        "$OpenSubtitlesApiUrl/subtitles/movie/$imdbId.json"
    } else {
        "$OpenSubtitlesApiUrl/subtitles/series/$imdbId:$season:$episode.json"
    }

    try {
        val res = app.get(url).parsedSafe<SubtitlesAPI>()
        res?.subtitles?.forEach { sub ->
            val langName = getLanguage(sub.lang).replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }
            subtitleCallback.invoke(
                SubtitleFile(
                    langName,
                    sub.url
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// API: WyZIE
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
             subtitleCallback.invoke(
                SubtitleFile(
                    sub.display,
                    sub.url
                )
            )
        }
    } catch (e: Exception) {
         e.printStackTrace()
    }
}
