package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// PENTING: Mewarisi TmdbProvider untuk tampilan cantik
class AdiDewasa : TmdbProvider() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // --- 1. TAMPILAN HALAMAN UTAMA (TMDB) ---
    // Mengambil daftar film populer langsung dari TMDB agar gambar jernih
    override val mainPage = mainPageOf(
        "movie/popular" to "Populer Minggu Ini",
        "movie/top_rated" to "Rating Tertinggi",
        "trending/movie/day" to "Sedang Trending",
        "discover/movie?with_genres=10749" to "Film Romantis (Romance)", // Genre ID 10749 = Romance
        "discover/movie?with_genres=18" to "Film Drama"
    )

    // --- 2. PENCARIAN (TMDB) ---
    // Menggunakan pencarian TMDB agar hasil akurat dan ada poster
    override suspend fun search(query: String): List<SearchResponse> {
        // Cari di TMDB dulu
        return super.search(query)
    }

    // --- 3. DETAIL HALAMAN (TMDB) ---
    // Fungsi load() ini otomatis dipanggil oleh TmdbProvider untuk menampilkan Cast, Rating, dll.
    // Kita tidak perlu menulis ulang logika UI-nya, cukup biarkan TmdbProvider bekerja.
    
    // --- 4. MENCARI LINK VIDEO (LOGIKA UTAMA) ---
    // Di sini kita menghubungkan metadata TMDB yang cantik dengan link video dari dramafull.cc
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Data yang masuk di sini berasal dari TMDB (berisi ID, Judul, Tahun, dll)
        val tmdbData = try {
            parseJson<LinkData>(data)
        } catch (e: Exception) {
            null
        }

        // Ambil Judul dan Tahun dari TMDB
        // Jika gagal parsing, coba fallback manual (jarang terjadi di TmdbProvider)
        val title = tmdbData?.title ?: "" 
        val year = tmdbData?.year
        val season = tmdbData?.season
        val episode = tmdbData?.episode
        val type = if (season != null) "tv" else "movie"

        if (title.isEmpty()) return false

        // --- STEP A: CARI FILM DI WEBSITE DRAMAFULL ---
        // Kita harus mencari judul film ini di dramafull.cc secara manual
        // karena ID TMDB tidak nyambung dengan ID website.
        val searchUrl = "$mainUrl/api/live-search/${title.replace(" ", "%20")}"
        
        try {
            val searchRes = app.get(searchUrl).parsedSafe<ApiSearchResponse>()
            
            // Cari hasil yang paling cocok berdasarkan Judul
            val matchedItem = searchRes?.data?.find { item ->
                val itemTitle = item.title ?: item.name ?: ""
                // Cek kemiripan judul (Simple check)
                itemTitle.contains(title, ignoreCase = true)
            }

            if (matchedItem == null || matchedItem.slug == null) {
                // Film ada di TMDB tapi tidak ada di Dramafull
                return false
            }

            // --- STEP B: LOAD HALAMAN DRAMAFULL ---
            val filmUrl = "$mainUrl/film/${matchedItem.slug}"
            val doc = app.get(filmUrl).document

            // Jika Serial TV, cari episode yang diminta
            var targetUrl = filmUrl
            if (season != null && episode != null) {
                // Logika mencari episode spesifik di halaman dramafull
                // Biasanya format: Episode 1, Episode 2, dst.
                val epLink = doc.select("div.episode-item a, .episode-list a").find { 
                    val text = it.text()
                    text.contains("Episode $episode", ignoreCase = true) || text.trim() == "$episode"
                }?.attr("href")
                
                if (epLink != null) targetUrl = epLink
            } else {
                // Jika Movie, ambil link tombol nonton
                targetUrl = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: filmUrl
            }

            // --- STEP C: EKSTRAK VIDEO (SAMA SEPERTI SEBELUMNYA) ---
            val videoDoc = app.get(targetUrl).document
            val script = videoDoc.select("script:containsData(signedUrl)").firstOrNull()?.toString()
            
            if (script != null) {
                val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(script)?.groupValues?.get(1)?.replace("\\/", "/")
                
                if (signedUrl != null) {
                    val res = app.get(signedUrl).text
                    val resJson = org.json.JSONObject(res)
                    val videoSource = resJson.optJSONObject("video_source")
                    
                    val qualities = videoSource?.keys()?.asSequence()?.toList()?.sortedByDescending { it.toIntOrNull() ?: 0 }
                    val bestQualityKey = qualities?.firstOrNull()
                    val bestQualityUrl = videoSource?.optString(bestQualityKey)

                    if (!bestQualityUrl.isNullOrEmpty()) {
                        callback(
                            com.lagradost.cloudstream3.utils.newExtractorLink(
                                name,
                                name,
                                bestQualityUrl,
                                com.lagradost.cloudstream3.utils.ExtractorLinkType.AUTO
                            )
                        )
                    }
                }
            }

            // --- STEP D: SUBTITLE (MENGGUNAKAN LOGIKA YANG SUDAH DIBUAT) ---
            // Karena kita sudah punya ID IMDb dari TMDB (otomatis dari TmdbProvider),
            // kita bisa langsung panggil subtitle tanpa scraping manual.
            val tmdbImdbId = tmdbData?.imdbId // TmdbProvider biasanya menyertakan ini
            
            if (tmdbImdbId != null) {
                 CoroutineScope(Dispatchers.IO).launch {
                    AdiDewasaSubtitles.invokeSubtitleAPI(tmdbImdbId, season, episode, subtitleCallback)
                    AdiDewasaSubtitles.invokeWyZIESUBAPI(tmdbImdbId, season, episode, subtitleCallback)
                }
            } else {
                // Fallback cari ID via Cinemeta jika TMDB gagal kirim ID IMDb
                CoroutineScope(Dispatchers.IO).launch {
                    val id = AdiDewasaSubtitles.getImdbIdFromCinemeta(title, year, type)
                    if (id != null) {
                        AdiDewasaSubtitles.invokeSubtitleAPI(id, season, episode, subtitleCallback)
                        AdiDewasaSubtitles.invokeWyZIESUBAPI(id, season, episode, subtitleCallback)
                    }
                }
            }

            return true

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
