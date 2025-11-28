package com.AdiDewasa

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.util.Locale

// Constants untuk API Subtitle & Helper
const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
const val WyZIESUBAPI = "https://sub.wyzie.ru"
const val tmdbAPI = "https://api.themoviedb.org/3"
const val apiKey = "b030404650f279792a8d3287232358e3" // Menggunakan Key public umum

// Helper untuk mendapatkan nama bahasa
fun getLanguage(code: String): String {
    return try {
        Locale(code).displayLanguage
    } catch (e: Exception) {
        code
    }
}

// Helper Pintar: Mencari IMDB ID berdasarkan Judul & Tahun via TMDB
// Ini diperlukan karena API Subtitle butuh IMDB ID, tapi AdiDewasa cuma punya Judul.
suspend fun getImdbIdFromTitle(title: String, year: Int?, type: TvType): String? {
    try {
        val searchType = if (type == TvType.Movie) "movie" else "tv"
        val query = title.replace(" ", "+")
        
        // Logic tahun untuk pencarian lebih akurat
        val yearParam = if (year != null) {
            if (type == TvType.Movie) "&primary_release_year=$year" else "&first_air_date_year=$year"
        } else ""

        // 1. Cari ID TMDB dulu
        val searchUrl = "$tmdbAPI/search/$searchType?api_key=$apiKey&query=$query$yearParam"
        val searchRes = app.get(searchUrl).parsedSafe<TmdbSearchResponse>()
        val result = searchRes?.results?.firstOrNull() ?: return null

        // 2. Konversi TMDB ID ke IMDB ID
        val externalUrl = "$tmdbAPI/$searchType/${result.id}/external_ids?api_key=$apiKey"
        val externalRes = app.get(externalUrl).parsedSafe<TmdbExternalIds>()

        return externalRes?.imdb_id
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

// 1. Fitur: OpenSubtitles v3 (via Stremio)
suspend fun invokeSubtitleAPI(
    imdbId: String?,
    season: Int? = null,
    episode: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
) {
    if (imdbId == null) return
    
    // Format URL berbeda untuk Movie dan TV
    val url = if (season == null) {
        "$SubtitlesAPI/subtitles/movie/$imdbId.json"
    } else {
        "$SubtitlesAPI/subtitles/series/$imdbId:$season:$episode.json"
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

// 2. Fitur: WyZIE Subs
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
                    sub.display, // Biasanya format "English", "Indonesian", dll
                    sub.url
                )
            )
        }
    } catch (e: Exception) {
         e.printStackTrace()
    }
}
