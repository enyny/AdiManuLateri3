package com.AdiManuLateri3

import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeSources
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeSubtitleAPI
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWyzie

open class Lateri3Play : TmdbProvider() {
    override var name = "Lateri3Play"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // Menggunakan API Key yang Anda berikan
    private val tmdbApiKey = BuildConfig.TMDB_API

    override val mainPage = mainPageOf(
        "/trending/all/day?api_key=$tmdbApiKey&region=ID" to "Trending (Indonesia)",
        "/movie/popular?api_key=$tmdbApiKey&region=ID" to "Film Populer",
        "/tv/popular?api_key=$tmdbApiKey&region=ID" to "Serial Populer",
        "/movie/top_rated?api_key=$tmdbApiKey&region=ID" to "Film Terbaik",
        "/tv/top_rated?api_key=$tmdbApiKey&region=ID" to "Serial Terbaik",
        "/discover/tv?api_key=$tmdbApiKey&with_original_language=ko" to "Drama Korea",
        "/discover/tv?api_key=$tmdbApiKey&with_keywords=210024|222243" to "Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): com.lagradost.cloudstream3.HomePageResponse {
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val url = "https://api.themoviedb.org/3${request.data}&page=$page"
        
        val res = app.get(url).parsedSafe<TmdbConfig.Results>() 
            ?: throw Exception("Gagal memuat data dari TMDB")
            
        val home = res.results.mapNotNull { media ->
            media.toSearchResponse(type)
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun TmdbConfig.Media.toSearchResponse(type: String? = null): com.lagradost.cloudstream3.SearchResponse? {
        val finalType = this.mediaType ?: type ?: "movie"
        return newMovieSearchResponse(
            this.title ?: this.name ?: this.originalTitle ?: return null,
            LinkData(this.id, finalType).toJson(),
            if (finalType == "movie") TvType.Movie else TvType.TvSeries,
        ) {
            this.posterUrl = "https://image.tmdb.org/t/p/w500${this.posterPath}"
            this.year = (this.releaseDate ?: this.firstAirDate)?.split("-")?.first()?.toIntOrNull()
        }
    }

    override suspend fun search(query: String): List<com.lagradost.cloudstream3.SearchResponse> {
        val url = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbApiKey&query=$query&page=1&include_adult=false"
        val res = app.get(url).parsedSafe<TmdbConfig.Results>()?.results ?: return emptyList()
        
        return res.mapNotNull { 
            it.toSearchResponse() 
        }
    }

    override suspend fun load(url: String): com.lagradost.cloudstream3.LoadResponse? {
        val data = parseJson<LinkData>(url)
        val type = data.type
        val append = "external_ids,credits,videos,recommendations"
        val loadUrl = "https://api.themoviedb.org/3/$type/${data.id}?api_key=$tmdbApiKey&append_to_response=$append"
        
        val res = app.get(loadUrl).parsedSafe<TmdbConfig.MediaDetail>() 
            ?: throw Exception("Gagal memuat detail")

        val title = res.title ?: res.name ?: return null
        val poster = "https://image.tmdb.org/t/p/original${res.posterPath}"
        val bgPoster = "https://image.tmdb.org/t/p/original${res.backdropPath}"
        val year = (res.releaseDate ?: res.firstAirDate)?.split("-")?.first()?.toIntOrNull()
        
        val trailer = res.videos?.results?.firstOrNull { it.type == "Trailer" && it.site == "YouTube" }?.key?.let { 
            "https://www.youtube.com/watch?v=$it" 
        }

        if (type == "tv") {
            val episodes = res.seasons?.mapNotNull { season ->
                val seasonUrl = "https://api.themoviedb.org/3/tv/${data.id}/season/${season.seasonNumber}?api_key=$tmdbApiKey"
                app.get(seasonUrl).parsedSafe<TmdbConfig.SeasonDetail>()?.episodes?.map { eps ->
                    newEpisode(
                        LinkData(
                            data.id,
                            "tv",
                            eps.seasonNumber,
                            eps.episodeNumber,
                            title = title,
                            year = year,
                            imdbId = res.externalIds?.imdbId
                        ).toJson()
                    ) {
                        this.name = eps.name
                        this.season = eps.seasonNumber
                        this.episode = eps.episodeNumber
                        this.posterUrl = "https://image.tmdb.org/t/p/w500${eps.stillPath}"
                        this.description = eps.overview
                        this.addDate(eps.airDate)
                    }
                }
            }?.flatten() ?: listOf()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = res.genres?.map { it.name }
                this.rating = res.voteAverage?.toInt()
                addTMDbId(data.id.toString())
                addImdbId(res.externalIds?.imdbId)
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, LinkData(
                data.id, 
                "movie",
                title = title,
                year = year,
                imdbId = res.externalIds?.imdbId
            ).toJson()) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = res.genres?.map { it.name }
                this.rating = res.voteAverage?.toInt()
                addTMDbId(data.id.toString())
                addImdbId(res.externalIds?.imdbId)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<LinkData>(data)

        // Panggil Subtitle (Wyzie & SubtitleAPI)
        invokeWyzie(linkData.imdbId, linkData.season, linkData.episode, subtitleCallback)
        invokeSubtitleAPI(linkData.imdbId, linkData.season, linkData.episode, subtitleCallback)

        // Panggil Source Video (Logic ada di Lateri3PlayExtractor)
        invokeSources(linkData, subtitleCallback, callback)

        return true
    }

    // Data Class untuk menyimpan info link
    data class LinkData(
        val id: Int? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val year: Int? = null,
        val imdbId: String? = null
    )
}
