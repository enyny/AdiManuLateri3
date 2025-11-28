package com.AdiDewasa

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.CommonActivity.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Mewarisi TmdbProvider agar punya akses ke fitur detail film cantik
class AdiDewasa : TmdbProvider() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // --- 1. HALAMAN UTAMA (ASLI ADIDEWASA) ---
    // Tetap menggunakan kategori asli sesuai permintaan
    override val mainPage: List<MainPageData>
        get() = listOf(
            MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
            MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
            MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
            MainPageData("Adult Movies - Sesuai Abjad (A-Z)", "2:3:adult"),
            MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
            MainPageData("All Collections - Baru Ditambahkan", "-1:1:adult"),
            MainPageData("All Collections - Paling Sering Ditonton", "-1:5:adult")
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            // Logic asli AdiDewasa untuk mengambil data dari website
            val dataParts = request.data.split(":")
            val type = dataParts.getOrNull(0) ?: "-1"
            val sort = dataParts.getOrNull(1)?.toIntOrNull() ?: 1
            val adultFlag = dataParts.getOrNull(2) ?: "normal"
            val isAdultSection = adultFlag == "adult"

            val jsonPayload = """{
                "page": $page,
                "type": "$type",
                "country": -1,
                "sort": $sort,
                "adult": true,
                "adultOnly": $isAdultSection,
                "ignoreWatched": false,
                "genres": [],
                "keyword": ""
            }""".trimIndent()

            val payload = jsonPayload.toRequestBody("application/json".toMediaType())
            val response = app.post("$mainUrl/api/filter", requestBody = payload)
            val homeResponse = response.parsedSafe<HomeResponse>()
            
            if (homeResponse?.success == false) return newHomePageResponse(emptyList(), false)

            val mediaList = homeResponse?.data ?: emptyList()
            val searchResults = mediaList.mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(name = request.name, list = searchResults, isHorizontalImages = false),
                hasNext = homeResponse?.nextPageUrl != null
            )
        } catch (e: Exception) {
            return newHomePageResponse(emptyList(), false)
        }
    }

    private fun MediaItem.toSearchResult(): SearchResponse? {
        val itemTitle = this.title ?: this.name ?: return null
        val itemSlug = this.slug ?: return null
        val itemImage = this.image ?: this.poster ?: ""
        val href = "$mainUrl/film/$itemSlug"
        
        // Poster URL fix
        val posterUrl = if (itemImage.isNotEmpty()) {
            if (itemImage.startsWith("http")) itemImage else mainUrl + itemImage
        } else ""

        return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Tetap gunakan search Dramafull agar hasil sinkron dengan konten yang tersedia
        try {
            val url = "$mainUrl/api/live-search/$query"
            val response = app.get(url)
            val searchResponse = response.parsedSafe<ApiSearchResponse>()
            return searchResponse?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            return null
        }
    }

    // --- 2. LOAD DETAIL (HYBRID: DRAMAFULL -> TMDB) ---
    // Ini bagian magic-nya. Mengambil URL Dramafull, tapi menampilkan data TMDB.
    override suspend fun load(url: String): LoadResponse {
        try {
            // A. Ambil Info Dasar dari Dramafull (Judul & Tahun)
            val doc = app.get(url).document
            val rawTitle = doc.selectFirst("div.right-info h1, h1.title")?.text() ?: "Unknown"
            val cleanTitle = rawTitle.replace(Regex("""\(\d{4}\)"""), "").trim()
            val year = Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
            
            // Cek apakah ini TV Series atau Movie berdasarkan elemen HTML
            val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list").isNotEmpty()
            val type = if (hasEpisodes) TvType.TvSeries else TvType.Movie
            
            // B. Cari Data Cantik di TMDB
            var tmdbLoadResponse: LoadResponse? = null
            
            try {
                // Cari di TMDB menggunakan judul dari Dramafull
                val tmdbSearch = super.search(cleanTitle)
                // Filter hasil yang tahunnya mirip (+/- 1 tahun)
                val match = tmdbSearch.find { searchRes ->
                    val resYear = (searchRes as? MovieSearchResponse)?.year ?: (searchRes as? TvSeriesSearchResponse)?.year
                    if (year != null && resYear != null) {
                        kotlin.math.abs(resYear - year) <= 1
                    } else true
                }

                if (match != null && match.url.isNotBlank()) {
                    // Load detail lengkap dari TMDB (Cast, Trailer, Plot, dll)
                    tmdbLoadResponse = super.load(match.url)
                }
            } catch (e: Exception) {
                // Ignore error TMDB, fallback ke manual
            }

            // C. Siapkan Data Link (Episode/Video) dari Dramafull
            val videoHref = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: url
            
            // Generate JSON LinkData untuk dikirim ke LoadLinks
            // Kita simpan URL Dramafull di sini agar LoadLinks tahu harus ambil video dari mana
            fun makeData(epNum: Int?, epUrl: String): String {
                return LinkData(
                    url = epUrl,
                    imdbId = (tmdbLoadResponse as? MovieLoadResponse)?.data?.let { try { parseJson<LinkData>(it).imdbId } catch(e:Exception){null} } 
                             ?: (tmdbLoadResponse as? TvSeriesLoadResponse)?.data?.let { try { parseJson<LinkData>(it).imdbId } catch(e:Exception){null} },
                    title = cleanTitle,
                    year = year,
                    episode = epNum,
                    season = null,
                    type = if (type == TvType.Movie) "movie" else "series"
                ).toJson()
            }

            // D. Gabungkan Data TMDB dengan Link Dramafull
            if (tmdbLoadResponse != null) {
                // Jika ketemu di TMDB, pakai UI TMDB tapi ganti daftar episodenya/datanya dengan link Dramafull
                if (type == TvType.TvSeries && tmdbLoadResponse is TvSeriesLoadResponse) {
                    // Mapping episode Dramafull
                    val realEpisodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
                        val epText = it.text().trim()
                        val epNum = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                        val epHref = it.attr("href")
                        if (epHref.isNotEmpty()) {
                            newEpisode(makeData(epNum, epHref)) {
                                this.name = "Episode ${epNum ?: epText}"
                                this.episode = epNum
                            }
                        } else null
                    }
                    // Return UI TMDB dengan Episode Dramafull
                    return tmdbLoadResponse.copy(episodes = realEpisodes)
                } else {
                    // Return UI TMDB dengan Link Video Dramafull (disimpan di 'data')
                    // Kita harus memodifikasi field 'data' di dalam LoadResponse
                    // Karena data class immutable, kita buat clone manual atau instance baru
                    // Cara paling aman: Return object baru dengan metadata TMDB
                    return newMovieLoadResponse(tmdbLoadResponse.name, url, TvType.Movie, makeData(null, videoHref)) {
                        this.posterUrl = tmdbLoadResponse.posterUrl
                        this.backgroundPosterUrl = tmdbLoadResponse.backgroundPosterUrl
                        this.year = tmdbLoadResponse.year
                        this.plot = tmdbLoadResponse.plot
                        this.tags = tmdbLoadResponse.tags
                        this.rating = tmdbLoadResponse.rating
                        this.actors = tmdbLoadResponse.actors
                        this.recommendations = tmdbLoadResponse.recommendations
                    }
                }
            } 

            // E. FALLBACK (Jika TMDB Gagal/Tidak Ketemu) -> Pakai Tampilan Manual Lama
            val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
            val desc = doc.selectFirst("div.right-info p.summary-content, .summary p")?.text() ?: ""
            
            if (type == TvType.TvSeries) {
                val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
                    val epNum = Regex("""(\d+)""").find(it.text())?.groupValues?.get(1)?.toIntOrNull()
                    val href = it.attr("href")
                    if(href.isNotEmpty()) newEpisode(makeData(epNum, href)) { this.episode = epNum; this.name = "Episode $epNum" } else null
                }
                return newTvSeriesLoadResponse(cleanTitle, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster; this.plot = desc; this.year = year
                }
            } else {
                return newMovieLoadResponse(cleanTitle, url, TvType.Movie, makeData(null, videoHref)) {
                    this.posterUrl = poster; this.plot = desc; this.year = year
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // --- 3. LOAD LINKS (SAMA SEPERTI SEBELUMNYA) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val linkData = try { parseJson<LinkData>(data) } catch (e: Exception) { LinkData(data) }
            val url = linkData.url // Ini link asli Dramafull
            
            // Subtitle Automation
            CoroutineScope(Dispatchers.IO).launch {
                var finalImdbId = linkData.imdbId
                if (finalImdbId.isNullOrBlank() && !linkData.title.isNullOrBlank()) {
                    finalImdbId = AdiDewasaSubtitles.getImdbIdFromCinemeta(
                        linkData.title, linkData.year, linkData.type ?: "movie"
                    )
                }
                if (!finalImdbId.isNullOrBlank()) {
                    showToast("Subtitle ID: $finalImdbId")
                    AdiDewasaSubtitles.invokeSubtitleAPI(finalImdbId, linkData.season, linkData.episode, subtitleCallback)
                    AdiDewasaSubtitles.invokeWyZIESUBAPI(finalImdbId, linkData.season, linkData.episode, subtitleCallback)
                }
            }

            // Video Extraction (Dramafull)
            val doc = app.get(url).document
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return false
            val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") ?: return false
            
            val resJson = JSONObject(app.get(signedUrl).text)
            val videoSource = resJson.optJSONObject("video_source") ?: return false
            val bestQualityKey = videoSource.keys().asSequence().toList().sortedByDescending { it.toIntOrNull() ?: 0 }.firstOrNull() ?: return false
            val bestQualityUrl = videoSource.optString(bestQualityKey)

            if (bestQualityUrl.isNotEmpty()) {
                callback(newExtractorLink(name, name, bestQualityUrl))
                // Subtitle bawaan server (jika ada)
                resJson.optJSONObject("sub")?.optJSONArray(bestQualityKey)?.let { array ->
                    for (i in 0 until array.length()) {
                        subtitleCallback(newSubtitleFile("English (Source)", mainUrl + array.getString(i)))
                    }
                }
                return true
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }
}
