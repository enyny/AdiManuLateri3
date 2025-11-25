package com.AdiDewasa

// Import Extractor
import com.AdiDewasa.AdiDewasaExtractor.invokeIdlix
import com.AdiDewasa.AdiDewasaExtractor.invokeMapple
import com.AdiDewasa.AdiDewasaExtractor.invokeVidfast
import com.AdiDewasa.AdiDewasaExtractor.invokeVidlink
import com.AdiDewasa.AdiDewasaExtractor.invokeVidsrc
import com.AdiDewasa.AdiDewasaExtractor.invokeXprime
import com.AdiDewasa.AdiDewasaExtractor.invokeVixsrc

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // API Key Publik TMDb (Biasa digunakan di open source)
    private val tmdbApi = "https://api.themoviedb.org/3"
    private val tmdbKey = "8d6d91941230817f7807d643736e8412"

    override val mainPage: List<MainPageData>
        get() = listOf(
            MainPageData("Adult Movies - Baru Rilis", "2:1:adult"),
            MainPageData("Adult Movies - Paling Populer", "2:5:adult"),
            MainPageData("Adult Movies - Rating Tertinggi", "2:6:adult"),
            MainPageData("Adult TV Shows - Episode Baru", "1:1:adult"),
            MainPageData("All Collections - Rekomendasi", "-1:6:adult")
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
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
            
            if (homeResponse?.success == false) {
                return newHomePageResponse(emptyList(), hasNext = false)
            }

            val mediaList = homeResponse?.data ?: emptyList()
            val searchResults = mediaList.mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(request.name, searchResults, false),
                hasNext = homeResponse?.nextPageUrl != null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    private fun MediaItem.toSearchResult(): SearchResponse? {
        val itemTitle = this.title ?: this.name ?: return null
        val itemSlug = this.slug ?: return null
        val itemImage = this.image?.takeIf { it.isNotEmpty() } ?: this.poster

        val href = "$mainUrl/film/$itemSlug"
        val posterUrl = if (!itemImage.isNullOrEmpty()) {
            if (itemImage.startsWith("http")) itemImage else mainUrl + itemImage
        } else null

        return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/api/live-search/$query"
        return try {
            app.get(url).parsedSafe<ApiSearchResponse>()?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            null
        }
    }

    // --- FUNGSI PENCARI TMDB ---
    private suspend fun fetchTmdbInfo(title: String, year: Int?): TmdbResult? {
        return try {
            val cleanQuery = title.replace(" ", "%20")
            val yearParam = if (year != null) "&year=$year" else ""
            val searchUrl = "$tmdbApi/search/movie?api_key=$tmdbKey&query=$cleanQuery$yearParam&include_adult=true"
            
            val res = app.get(searchUrl).parsedSafe<TmdbSearchResponse>()
            // Ambil hasil pertama
            res?.results?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        // 1. Bersihkan Judul dari Dramafull
        val ogTitle = document.select("meta[property=og:title]").attr("content")
        val fallbackTitle = document.selectFirst("h1")?.text() ?: "Unknown Title"
        val rawTitle = if (ogTitle.isNotEmpty()) ogTitle else fallbackTitle

        val title = rawTitle
            .replace(Regex("(?i)^Watch\\s+"), "")
            .substringBefore(" - Movie")
            .substringBefore(" subbed")
            .substringBefore(" online")
            .trim()

        // 2. Ambil Tahun dari Dramafull
        var year = Regex("\\d{4}").find(title)?.value?.toIntOrNull()
        if (year == null) year = Regex("\\d{4}").find(document.text())?.value?.toIntOrNull()

        // 3. CARI KE TMDB (Logika Baru)
        val tmdbInfo = fetchTmdbInfo(title, year)
        
        // Metadata Priority: TMDb > Dramafull
        val finalTitle = tmdbInfo?.title ?: title
        val finalYear = tmdbInfo?.releaseDate?.take(4)?.toIntOrNull() ?: year
        val finalPlot = tmdbInfo?.overview ?: document.select("meta[property=og:description]").attr("content")
        val finalPoster = if (tmdbInfo?.posterPath != null) "https://image.tmdb.org/t/p/w500${tmdbInfo.posterPath}" 
                          else document.select("meta[property=og:image]").attr("content")
        val finalBackdrop = if (tmdbInfo?.backdropPath != null) "https://image.tmdb.org/t/p/w780${tmdbInfo.backdropPath}" else null
        val tmdbId = tmdbInfo?.id

        // Logika Episode
        val episodes = document.select(".episode-list a, .episode-item a, ul.episodes li a").mapNotNull {
            val text = it.text()
            val href = it.attr("href")
            val epNum = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() 
                ?: Regex("""Episode\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            
            if (epNum == null) return@mapNotNull null
            
            // Bungkus data untuk loadLinks
            val data = LinkData(
                url = fixUrl(href),
                title = finalTitle,
                year = finalYear,
                season = 1,
                episode = epNum,
                tmdbId = tmdbId
            )
            
            newEpisode(data.toJson()) { 
                this.name = "Episode $epNum"
                this.episode = epNum
            }
        }

        val tvType = if (episodes.isNotEmpty()) TvType.TvSeries else TvType.Movie
        
        // Data untuk Movie (JSON)
        val movieData = LinkData(url, finalTitle, finalYear, tmdbId = tmdbId).toJson()

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(finalTitle, url, tvType, episodes) {
                this.posterUrl = finalPoster
                this.backgroundPosterUrl = finalBackdrop
                this.plot = finalPlot
                this.year = finalYear
            }
        } else {
            newMovieLoadResponse(finalTitle, url, tvType, movieData) {
                this.posterUrl = finalPoster
                this.backgroundPosterUrl = finalBackdrop
                this.plot = finalPlot
                this.year = finalYear
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false

        // 1. Parse JSON
        val linkData = try {
            parseJson<LinkData>(data)
        } catch (e: Exception) {
            // Fallback jika data masih string URL lama
            LinkData(url = data, title = "")
        }

        val originalUrl = linkData.url
        val cleanTitle = linkData.title
        val tmdbId = linkData.tmdbId

        // 2. INTERNAL PLAYER (Dramafull Original) - Prioritas 1
        try {
            val doc = app.get(originalUrl).document
            val allScripts = doc.select("script").joinToString(" ") { it.data() }
            
            // Regex Original yang sudah terbukti bekerja
            val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(allScripts)
                ?.groupValues?.get(1)?.replace("\\/", "/") 
                ?: Regex("""signedUrl\s*=\s*['"]([^'"]+)['"]""").find(allScripts)?.groupValues?.get(1)?.replace("\\/", "/")

            if (signedUrl != null) {
                val jsonText = app.get(
                    signedUrl, 
                    referer = originalUrl,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).text
                
                val json = JSONObject(jsonText)
                val videoSource = json.optJSONObject("video_source")
                
                if (videoSource != null) {
                    val qualities = videoSource.keys().asSequence().toList()
                    qualities.forEach { qualityStr ->
                        val link = videoSource.optString(qualityStr)
                        if (link.isNotEmpty()) {
                            callback.invoke(
                                newExtractorLink(
                                    this.name,
                                    "AdiDewasa $qualityStr",
                                    link,
                                    ExtractorLinkType.M3U8
                                ) {
                                    this.referer = originalUrl
                                }
                            )
                        }
                    }
                    
                    // Subtitles Internal
                    val subJson = json.optJSONObject("sub")
                    val bestQuality = qualities.maxByOrNull { it.toIntOrNull() ?: 0 }
                    if (bestQuality != null) {
                        val subs = subJson?.optJSONArray(bestQuality)
                        if (subs != null) {
                            for (i in 0 until subs.length()) {
                                val subPath = subs.getString(i)
                                val subUrl = if (subPath.startsWith("http")) subPath else "$mainUrl$subPath"
                                subtitleCallback.invoke(newSubtitleFile("English", subUrl))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Internal gagal, lanjut ke eksternal
        }

        // 3. EXTERNAL EXTRACTORS (Idlix, Vidsrc, dll) - Menggunakan TMDb ID atau Title
        if (cleanTitle.isNotEmpty()) {
            runAllAsync(
                // Idlix pakai Title (pencarian string)
                { invokeIdlix(cleanTitle, linkData.year, linkData.season, linkData.episode, subtitleCallback, callback) },
                // Vidsrc pakai TMDb ID jika ada (lebih akurat), atau Title
                { 
                     // Logic Vidsrc di Extractor harusnya support tmdbId jika dimodif, tapi defaultnya imdb
                     // Kita kirim null imdbId, dan biarkan extractor pakai season/eps jika perlu
                     invokeVidsrc(null, linkData.season, linkData.episode, subtitleCallback, callback) 
                },
                // Xprime butuh TMDB ID
                { invokeXprime(tmdbId, cleanTitle, linkData.year, linkData.season, linkData.episode, subtitleCallback, callback) },
                
                { invokeVidfast(tmdbId, linkData.season, linkData.episode, subtitleCallback, callback) },
                { invokeVidlink(tmdbId, linkData.season, linkData.episode, callback) },
                { invokeMapple(tmdbId, linkData.season, linkData.episode, subtitleCallback, callback) },
                { invokeVixsrc(tmdbId, linkData.season, linkData.episode, callback) }
            )
        }

        return true
    }
}
