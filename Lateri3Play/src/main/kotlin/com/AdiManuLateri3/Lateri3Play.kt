package com.AdiManuLateri3

import android.content.SharedPreferences
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.AdiManuLateri3.Lateri3PlayExtractor.invokePrimeSrc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRiveStream
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeSubtitleAPI
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWyZIESUBAPI

open class Lateri3Play(val sharedPref: SharedPreferences) : TmdbProvider() {
    override var name = "Lateri3Play"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    companion object {
        // API Configuration
        const val PrimeSrcApi = "https://primesrc.me"
        const val RiveStreamAPI = "https://rivestream.org"
        const val SubtitlesAPI = "https://opensubtitles-v3.strem.io"
        const val WyZIESUBAPI = "https://sub.wyzie.ru"
    }

    override val mainPage = mainPageOf(
        "trending/all/day" to "Trending",
        "movie/popular" to "Popular Movies",
        "tv/popular" to "Popular TV Shows",
        "movie/top_rated" to "Top Rated Movies",
        "tv/top_rated" to "Top Rated TV Shows",
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LinkData>(data)

        // Memuat Subtitle
        invokeSubtitleAPI(res.imdbId, res.season, res.episode, subtitleCallback)
        invokeWyZIESUBAPI(res.imdbId, res.season, res.episode, subtitleCallback)

        // Memuat Video Sources (Providers)
        // 1. PrimeWire
        invokePrimeSrc(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        
        // 2. RiveStream
        // Menggunakan TMDB ID untuk RiveStream
        invokeRiveStream(res.id, res.season, res.episode, callback)

        return true
    }

    // Data Class sederhana untuk passing data link internal
    data class LinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val year: Int? = null,
    )
}
