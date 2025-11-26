package com.pusatfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Pusatfilm : MainAPI() {

    override var mainUrl = "https://pusatfilm21.online"
    override var name = "Pusatfilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    // API Key Publik TMDb
    private val tmdbApiKey = "90b62d85429816503f8489849206d4e2"

    override val mainPage = mainPageOf(
        "film-terbaru/page/%d/" to "Film Terbaru",
        "trending/page/%d/" to "Film Trending",
        "genre/action/page/%d/" to "Film Action",
        "series-terbaru/page/%d/" to "Series Terbaru",
        "drama-korea/page/%d/" to "Drama Korea",
        "west-series/page/%d/" to "West Series",
        "drama-china/page/%d/" to "Drama China",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr()).fixImageQuality()
        
        val isSeries = href.contains("/tv/")
        
        return if (isSeries) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                val quality = this@toSearchResult.select("div.gmr-qual, div.gmr-quality-item > a").text().trim().replace("-", "")
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // 1. Ambil Data Dasar dari Pusatfilm
        val rawTitle = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown Title"
        
        // Bersihkan tahun dari judul (Misal: "Dune (2024)" -> "Dune")
        val cleanTitleRegex = Regex("(.*?)\\s*\\(\\d{4}\\)")
        val cleanTitle = cleanTitleRegex.find(rawTitle)?.groupValues?.get(1) ?: rawTitle
        
        val yearText = document.selectFirst("div.gmr-movie-date a")?.text()?.trim()
        val year = yearText?.toIntOrNull()

        // 2. Cari Data di TMDb
        val isSeries = url.contains("/tv/")
        val tmdbResult = getTmdbDetails(cleanTitle, year, isSeries)

        // Prioritas Gambar: TMDb -> Metadata Web -> Web Poster -> Fallback
        val poster = tmdbResult?.posterPath 
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.fixImageQuality()
            ?: document.selectFirst("div.gmr-poster img")?.getImageAttr()?.fixImageQuality()
        
        val background = tmdbResult?.backdropPath
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.fixImageQuality()

        val plot = tmdbResult?.overview 
            ?: document.selectFirst("div.entry-content p")?.text()?.trim()

        val tags = document.select("div.gmr-movie-genre a").map { it.text() }

        if (isSeries) {
            val episodes = document.select("div.gmr-listseries a").mapNotNull { eps ->
                val href = fixUrl(eps.attr("href"))
                val epsTitle = eps.attr("title")
                
                // Parsing Episode
                val episodeMatch = Regex("(?i)Episode\\s*(\\d+)").find(epsTitle)
                val episodeNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
                
                if (episodeNum == null) return@mapNotNull null

                newEpisode(href) {
                    this.name = "Episode $episodeNum"
                    this.episode = episodeNum
                    this.season = 1 
                }
            }.sortedBy { it.episode }

            return newTvSeriesLoadResponse(rawTitle, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = plot
                this.tags = tags
                // PERBAIKAN: Menggunakan addTmdbId (huruf d kecil)
                tmdbResult?.id?.let { addTmdbId(it.toString()) }
            }
        } else {
            return newMovieLoadResponse(rawTitle, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = plot
                this.tags = tags
                // PERBAIKAN: Menggunakan addTmdbId (huruf d kecil)
                tmdbResult?.id?.let { addTmdbId(it.toString()) }
            }
        }
    }

    // --- FUNGSI PENCARIAN TMDB ---
    private suspend fun getTmdbDetails(title: String, year: Int?, isSeries: Boolean): TmdbResult? {
        return try {
            val type = if (isSeries) "tv" else "movie"
            val searchUrl = "https://api.themoviedb.org/3/search/$type?api_key=$tmdbApiKey&query=$title&language=id-ID&page=1" +
                    if (year != null) "&year=$year" else ""
            
            val response = app.get(searchUrl).text
            val json = parseJson<TmdbSearchResponse>(response)
            
            val result = json.results.firstOrNull() ?: return null
            
            TmdbResult(
                id = result.id,
                posterPath = if (result.poster_path != null) "https://image.tmdb.org/t/p/w500${result.poster_path}" else null,
                backdropPath = if (result.backdrop_path != null) "https://image.tmdb.org/t/p/original${result.backdrop_path}" else null,
                overview = result.overview
            )
        } catch (e: Exception) {
            null
        }
    }

    data class TmdbSearchResponse(val results: List<TmdbItem>)
    data class TmdbItem(
        val id: Int,
        val poster_path: String?,
        val backdrop_path: String?,
        val overview: String?
    )
    data class TmdbResult(
        val id: Int,
        val posterPath: String?,
        val backdropPath: String?,
        val overview: String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        document.select("ul#dropdown-server li a").forEach {
            val encodedUrl = it.attr("data-frame")
            if (encodedUrl.isNotEmpty()) {
                val decodedUrl = base64Decode(encodedUrl)
                loadExtractor(decodedUrl, data, subtitleCallback, callback)
            }
        }
        
        document.select("div.gmr-embed-responsive iframe").forEach {
            val src = it.attr("src")
            if (src.isNotEmpty() && !src.contains("youtube")) {
                 loadExtractor(src, data, subtitleCallback, callback)
            }
        }

        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }
}
