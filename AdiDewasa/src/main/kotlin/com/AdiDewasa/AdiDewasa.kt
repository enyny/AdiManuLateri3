package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.* // PENTING: Mengimpor amap dan utilitas lainnya
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.SubtitleFile

class AdiDewasa : TmdbProvider() {
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val apiKey = "b030404650f279792a8d3287232358e3"
    private val tmdbApi = "https://api.themoviedb.org/3"

    // --- KONFIGURASI HALAMAN UTAMA (TMDb MODE) ---
    override val mainPage = mainPageOf(
        "$tmdbApi/discover/movie?api_key=$apiKey&include_adult=true&sort_by=popularity.desc" to "Adult Movies - Populer",
        "$tmdbApi/discover/movie?api_key=$apiKey&include_adult=true&sort_by=primary_release_date.desc" to "Adult Movies - Terbaru",
        "$tmdbApi/discover/movie?api_key=$apiKey&include_adult=true&sort_by=vote_average.desc&vote_count.gte=50" to "Adult Movies - Rating Tinggi",
        "$tmdbApi/discover/tv?api_key=$apiKey&include_adult=true&sort_by=popularity.desc" to "Adult TV - Populer",
        "$tmdbApi/discover/tv?api_key=$apiKey&include_adult=true&sort_by=first_air_date.desc" to "Adult TV - Terbaru"
    )

    // --- LOAD LINKS (PARALEL & HYBRID) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        // 1. Parsing Data dari TMDb
        val res = try {
            AppUtils.parseJson<LinkData>(data)
        } catch (e: Exception) {
            return false
        }

        val tmdbId = res.id // ID dari TMDB
        val title = res.title ?: res.orgTitle ?: ""
        val year = res.year
        val season = res.season
        val episode = res.episode

        // 2. Jalankan Extractor secara Paralel
        listOf(
            // TUGAS A: Wyzie (Subtitle)
            suspend {
                if (tmdbId != null) {
                    AdiDewasaUtils.invokeWyzieSubtitle(tmdbId, season, episode, subtitleCallback)
                }
            },

            // TUGAS B: Adimoviebox (Berdasarkan Judul)
            suspend {
                if (title.isNotEmpty()) {
                    AdiHybrid.invokeAdimoviebox(title, year, season, episode, subtitleCallback, callback)
                }
            },

            // TUGAS C: Idlix (Berdasarkan Judul)
            suspend {
                if (title.isNotEmpty()) {
                    AdiHybrid.invokeIdlix(title, year, season, episode, subtitleCallback, callback)
                }
            },

            // TUGAS D: Vidlink (Berdasarkan TMDB ID)
            suspend {
                if (tmdbId != null) {
                    AdiHybrid.invokeVidlink(tmdbId, season, episode, callback)
                }
            },

            // TUGAS E: Superembed (Berdasarkan TMDB ID)
            suspend {
                if (tmdbId != null) {
                    AdiHybrid.invokeSuperembed(tmdbId, season, episode, subtitleCallback, callback)
                }
            }
        ).amap { it.invoke() } // Eksekusi semua tugas secara paralel

        return true
    }

    // --- DATA CLASS UNTUK MENERIMA DATA DARI TMDB PROVIDER ---
    data class LinkData(
        val id: Int? = null,         // TMDB ID
        val imdbId: String? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val orgTitle: String? = null,
        val year: Int? = null
    )
}
