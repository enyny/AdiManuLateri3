package com.AdiDewasa

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object AdiDewasaSubtitles {

    private const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
    private const val WyZIESUBAPI = "https://sub.wyzie.ru"
    private const val CINEMETA_API = "https://v3-cinemeta.strem.io"

    // --- FUNGSI PENCARIAN CERDAS (SMART MATCH) ---
    suspend fun getImdbIdFromCinemeta(title: String, year: Int?, type: String): String? {
        try {
            // Bersihkan judul dari karakter aneh agar pencarian lebih akurat
            val cleanQuery = title.replace(Regex("""[^a-zA-Z0-9\s]"""), " ").trim()
            val encodedTitle = URLEncoder.encode(cleanQuery, "UTF-8")
            
            val searchUrl = "$CINEMETA_API/catalog/$type/top/search=$encodedTitle.json"
            val response = app.get(searchUrl).text
            val metaResponse = parseJson<CinemetaSearchResponse>(response)

            // Logika Penyaringan Ketat
            val matched = metaResponse.metas?.find { meta ->
                val metaName = meta.name ?: ""
                val metaYear = meta.releaseInfo?.take(4)?.toIntOrNull()

                // 1. Cek Kemiripan Judul (Minimal 85% mirip)
                val isTitleSimilar = getSimilarity(title, metaName) > 0.85

                // 2. Cek Tahun (Toleransi +/- 1 tahun)
                val isYearMatch = if (year != null && metaYear != null) {
                    kotlin.math.abs(metaYear - year) <= 1
                } else {
                    true // Jika tahun tidak ada, andalkan kemiripan judul saja
                }

                isTitleSimilar && isYearMatch
            }

            return matched?.imdb_id
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // --- Algoritma Kemiripan Teks (Jaro-Winkler simplified) ---
    private fun getSimilarity(s1: String, s2: String): Double {
        val str1 = s1.lowercase()
        val str2 = s2.lowercase()
        if (str1 == str2) return 1.0
        
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1
        if (longer.isEmpty()) return 1.0
        
        return (longer.length - levenshtein(longer, shorter)) / longer.length.toDouble()
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = Array(lhsLength + 1) { it }
        var newCost = Array(lhsLength + 1) { 0 }

        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }
    // ---------------------------------------------------------

    suspend fun invokeSubtitleAPI(imdbId: String?, season: Int?, episode: Int?, subtitleCallback: (SubtitleFile) -> Unit) {
        if (imdbId.isNullOrBlank()) return
        val url = if (season == null) "$SubtitlesAPI/subtitles/movie/$imdbId.json" 
                  else "$SubtitlesAPI/subtitles/series/$imdbId:$season:$episode.json"
        
        try {
            val res = app.get(url)
            if (res.code == 200) {
                // Ambil semua bahasa, tidak hanya Inggris
                res.parsedSafe<SubtitlesAPIResponse>()?.subtitles?.forEach {
                    if (it.url.isNotBlank()) {
                        subtitleCallback(newSubtitleFile(getLanguage(it.lang), it.url))
                    }
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
