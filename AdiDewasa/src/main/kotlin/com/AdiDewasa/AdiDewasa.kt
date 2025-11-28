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

class AdiDewasa : MainAPI() {
    override var mainUrl = "https://dramafull.cc"
    override var name = "AdiDewasa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    // API Key TMDB Public
    private val tmdbApiKey = "8d6d91941230817f7807d643736e8412"

    // --- 1. HALAMAN UTAMA (ASLI DRAMAFULL) ---
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
        
        val posterUrl = if (itemImage.isNotEmpty()) {
            if (itemImage.startsWith("http")) itemImage else mainUrl + itemImage
        } else ""

        return newMovieSearchResponse(itemTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        try {
            val url = "$mainUrl/api/live-search/$query"
            val response = app.get(url)
            val searchResponse = response.parsedSafe<ApiSearchResponse>()
            return searchResponse?.data?.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            return null
        }
    }

    // --- 2. LOAD DETAIL (HYBRID) ---
    override suspend fun load(url: String): LoadResponse {
        try {
            val doc = app.get(url).document
            val rawTitle = doc.selectFirst("div.right-info h1, h1.title")?.text() ?: "Unknown"
            val cleanTitle = rawTitle.replace(Regex("""\(\d{4}\)"""), "").trim()
            val year = Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
            
            val hasEpisodes = doc.select("div.tab-content.episode-button, .episodes-list").isNotEmpty()
            val type = if (hasEpisodes) TvType.TvSeries else TvType.Movie
            
            // B. Fetch Data TMDB
            var tmdbDetails: TmdbDetails? = null
            try {
                val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbApiKey&query=$cleanTitle"
                val searchRes = app.get(searchUrl).parsedSafe<TmdbSearchResponse>()
                
                val match = searchRes?.results?.find { res ->
                    val resYear = res.release_date?.take(4)?.toIntOrNull() ?: res.first_air_date?.take(4)?.toIntOrNull()
                    val yearMatch = if (year != null && resYear != null) kotlin.math.abs(resYear - year) <= 1 else true
                    val typeMatch = if (type == TvType.Movie) res.media_type == "movie" else res.media_type == "tv"
                    yearMatch && typeMatch
                }

                if (match != null) {
                    val detailUrl = "https://api.themoviedb.org/3/${match.media_type}/${match.id}?api_key=$tmdbApiKey&append_to_response=credits,recommendations,external_ids"
                    val detailsText = app.get(detailUrl).text
                    val json = JSONObject(detailsText)
                    
                    // FIX: Mapping Actor ke ActorData agar kompatibel
                    val actorDataList = json.optJSONObject("credits")?.optJSONArray("cast")?.let { cast ->
                        (0 until cast.length()).take(10).map { i ->
                            val c = cast.getJSONObject(i)
                            val name = c.optString("name")
                            val role = c.optString("character")
                            val img = "https://image.tmdb.org/t/p/w200" + c.optString("profile_path")
                            ActorData(Actor(name, img), roleString = role)
                        }
                    }

                    tmdbDetails = TmdbDetails(
                        overview = json.optString("overview"),
                        posterPath = "https://image.tmdb.org/t/p/w500" + json.optString("poster_path"),
                        backdropPath = "https://image.tmdb.org/t/p/original" + json.optString("backdrop_path"),
                        voteAverage = json.optDouble("vote_average"),
                        imdbId = json.optJSONObject("external_ids")?.optString("imdb_id"),
                        actors = actorDataList
                    )
                }
            } catch (e: Exception) { }

            // C. Link Video Dramafull
            val videoHref = doc.selectFirst("div.last-episode a, .watch-button a")?.attr("href") ?: url
            
            fun makePayload(epNum: Int?, epUrl: String): String {
                return LinkData(
                    url = epUrl,
                    imdbId = tmdbDetails?.imdbId,
                    title = cleanTitle,
                    year = year,
                    episode = epNum,
                    season = null,
                    type = if (type == TvType.Movie) "movie" else "series"
                ).toJson()
            }

            // D. Return LoadResponse
            val posterUrl = tmdbDetails?.posterPath ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
            val bgUrl = tmdbDetails?.backdropPath
            val plot = tmdbDetails?.overview ?: doc.selectFirst("div.right-info p.summary-content, .summary p")?.text()
            val score = tmdbDetails?.voteAverage?.let { Score.from10(it.toFloat()) }
            val actors = tmdbDetails?.actors

            if (type == TvType.TvSeries) {
                val episodes = doc.select("div.episode-item a, .episode-list a").mapNotNull {
                    val epText = it.text().trim()
                    val epNum = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                    val href = it.attr("href")
                    if (href.isNotEmpty()) {
                        newEpisode(makePayload(epNum, href)) {
                            this.name = "Episode ${epNum ?: epText}"
                            this.episode = epNum
                        }
                    } else null
                }
                return newTvSeriesLoadResponse(cleanTitle, url, TvType.TvSeries, episodes) {
                    this.year = year
                    this.posterUrl = posterUrl
                    this.backgroundPosterUrl = bgUrl
                    this.plot = plot
                    this.score = score
                    this.actors = actors
                }
            } else {
                return newMovieLoadResponse(cleanTitle, url, TvType.Movie, makePayload(null, videoHref)) {
                    this.year = year
                    this.posterUrl = posterUrl
                    this.backgroundPosterUrl = bgUrl
                    this.plot = plot
                    this.score = score
                    this.actors = actors
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // --- 3. LOAD LINKS ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val linkData = try { parseJson<LinkData>(data) } catch (e: Exception) { LinkData(data) }
            val url = linkData.url 
            
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

            val doc = app.get(url).document
            val script = doc.select("script:containsData(signedUrl)").firstOrNull()?.toString() ?: return false
            val signedUrl = Regex("""window\.signedUrl\s*=\s*"(.+?)"""").find(script)?.groupValues?.get(1)?.replace("\\/", "/") ?: return false
            
            val resJson = JSONObject(app.get(signedUrl).text)
            val videoSource = resJson.optJSONObject("video_source") ?: return false
            val bestQualityKey = videoSource.keys().asSequence().toList().sortedByDescending { it.toIntOrNull() ?: 0 }.firstOrNull() ?: return false
            val bestQualityUrl = videoSource.optString(bestQualityKey)

            if (bestQualityUrl.isNotEmpty()) {
                callback(newExtractorLink(name, name, bestQualityUrl))
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

    data class TmdbSearchResponse(val results: List<TmdbResult>?)
    data class TmdbResult(val id: Int, val title: String?, val name: String?, val release_date: String?, val first_air_date: String?, val media_type: String?)
    
    // FIX: Menggunakan List<ActorData>?
    data class TmdbDetails(
        val overview: String?, 
        val posterPath: String?, 
        val backdropPath: String?, 
        val voteAverage: Double, 
        val imdbId: String?,
        val actors: List<ActorData>?
    )
}
