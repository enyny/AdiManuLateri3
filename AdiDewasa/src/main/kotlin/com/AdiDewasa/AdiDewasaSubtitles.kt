package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.net.URLEncoder
import java.util.Locale

object AdiDewasaSubtitles {

    private const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
    private const val WyZIESUBAPI = "https://sub.wyzie.ru"
    private const val CINEMETA_API = "https://v3-cinemeta.strem.io"

    // Mencari ID IMDb berdasarkan Judul dan Tahun melalui API Cinemeta
    suspend fun getImdbIdFromCinemeta(title: String, year: Int?, type: String): String? {
        try {
            val cleanTitle = URLEncoder.encode(title, "UTF-8")
            // Cinemeta search endpoint
            val searchUrl = "$CINEMETA_API/catalog/$type/top/search=$cleanTitle.json"
            
            val response = app.get(searchUrl).text
            val metaResponse = parseJson<CinemetaSearchResponse>(response)

            // Cari hasil yang paling cocok
            val matched = metaResponse.metas?.find { meta ->
                val metaYear = meta.releaseInfo?.substring(0, 4)?.toIntOrNull()
                if (year != null && metaYear != null) {
                    metaYear == year // Prioritaskan tahun yang sama persis
                } else {
                    true // Ambil hasil pertama jika tahun tidak diketahui
                }
            }

            return matched?.imdb_id
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun invokeSubtitleAPI(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        if (imdbId.isNullOrBlank()) return
        val url = if (season == null) "$SubtitlesAPI/subtitles/movie/$imdbId.json" 
                  else "$SubtitlesAPI/subtitles/series/$imdbId:$season:$episode.json"
        
        try {
            val res = app.get(url)
            if (res.code == 200) {
                res.parsedSafe<SubtitlesAPIResponse>()?.subtitles?.forEach {
                    if (it.url.isNotBlank()) subtitleCallback(newSubtitleFile(getLanguage(it.lang), it.url))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun invokeWyZIESUBAPI(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        if (imdbId.isNullOrBlank()) return
        val url = buildString {
            append("$WyZIESUBAPI/search?id=$imdbId")
            if (season != null && episode != null) append("&season=$season&episode=$episode")
        }
        try {
            val res = app.get(url)
            if (res.code == 200) {
                parseJson<List<WyZIESUB>>(res.text).forEach {
                    if (it.url.isNotBlank()) subtitleCallback(newSubtitleFile(it.display, it.url))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getLanguage(code: String): String {
        val map = mapOf(
            "en" to "English", "id" to "Indonesian", "ms" to "Malay",
            "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
            "es" to "Spanish", "fr" to "French", "de" to "German",
            "ru" to "Russian", "hi" to "Hindi", "ar" to "Arabic",
            "pt" to "Portuguese", "it" to "Italian", "th" to "Thai",
            "vi" to "Vietnamese"
        )
        return map[code.lowercase()] ?: code
    }

    // Data classes
    data class SubtitlesAPIResponse(@JsonProperty("subtitles") val subtitles: List<Subtitle>?)
    data class Subtitle(val url: String, val lang: String)
    data class WyZIESUB(val url: String, val display: String)
    data class CinemetaSearchResponse(@JsonProperty("metas") val metas: List<CinemetaMeta>?)
    data class CinemetaMeta(
        @JsonProperty("imdb_id") val imdb_id: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("releaseInfo") val releaseInfo: String?
    )
}
