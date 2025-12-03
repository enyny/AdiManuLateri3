package com.AdiManuLateri3

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.runAllAsync
import com.fasterxml.jackson.annotation.JsonProperty

object Lateri3PlayExtractor {

    // Daftar Host yang diizinkan (10 Extractor Pilihan)
    private val ALLOWED_DOMAINS = listOf(
        "streamwish", "filemoon", "mp4upload", "dood", "mixdrop", 
        "streamtape", "voe", "vidhide", "ok.ru", "streamsb"
    )

    // --- LOGIKA UTAMA ---

    suspend fun invokeSources(
        data: Lateri3Play.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Jalankan pencarian sumber secara paralel
        runAllAsync(
            { invokeRidoMovies(data, subtitleCallback, callback) },
            { invokeNineTv(data, callback) }
        )
    }

    // --- 1. SUBTITLE API (Wyzie & OpenSubtitles) ---

    suspend fun invokeWyzie(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        if (imdbId == null) return
        val type = if (season != null) "tv" else "movie"
        val url = if (type == "movie") {
            "https://sub.wyzie.ru/search?id=$imdbId"
        } else {
            "https://sub.wyzie.ru/search?id=$imdbId&season=$season&episode=$episode"
        }

        try {
            val res = app.get(url).parsedSafe<List<WyzieSub>>() ?: return
            res.forEach { sub ->
                subtitleCallback(
                    newSubtitleFile(sub.display ?: sub.language ?: "Unknown", sub.url)
                )
            }
        } catch (e: Exception) {
            // Ignore error
        }
    }

    suspend fun invokeSubtitleAPI(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        if (imdbId == null) return
        val url = if (season == null) {
            "https://opensubtitles-v3.strem.io/subtitles/movie/$imdbId.json"
        } else {
            "https://opensubtitles-v3.strem.io/subtitles/series/$imdbId:$season:$episode.json"
        }

        try {
            val res = app.get(url).parsedSafe<OpenSubRes>() ?: return
            res.subtitles.forEach { sub ->
                subtitleCallback(
                    newSubtitleFile(sub.lang ?: "Unknown", sub.url)
                )
            }
        } catch (e: Exception) {
            // Ignore error
        }
    }

    // --- 2. SOURCE FINDER (Contoh Sumber Sederhana) ---

    // Sumber 1: RidoMovies (Biasanya menyediakan FileMoon, VidHide, StreamWish)
    private suspend fun invokeRidoMovies(
        data: Lateri3Play.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val base = "https://ridomovies.tv"
        try {
            // Cari konten
            val searchUrl = "$base/core/api/search?q=${data.imdbId ?: data.title}"
            val searchRes = app.get(searchUrl).parsedSafe<RidoSearch>()
            
            val item = searchRes?.data?.items?.find { 
                it.contentable?.imdbId == data.imdbId || it.title.equals(data.title, true)
            } ?: return

            val slug = item.slug ?: return
            
            // Tentukan URL Video
            val videoApiUrl = if (data.season == null) {
                "$base/core/api/movies/$slug/videos"
            } else {
                // Untuk TV Series, perlu cari ID episode dulu (disederhanakan)
                "$base/core/api/episodes/$slug-${data.season}x${data.episode}/videos"
            }

            val videos = app.get(videoApiUrl).parsedSafe<RidoVideos>()?.data ?: return

            videos.forEach { video ->
                val embedUrl = video.url ?: return@forEach
                // Filter hanya domain yang diizinkan
                if (ALLOWED_DOMAINS.any { embedUrl.contains(it, ignoreCase = true) }) {
                    loadExtractor(embedUrl, base, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    // Sumber 2: NineTV (MoviesAPI) - Sumber sederhana untuk embed umum
    private suspend fun invokeNineTv(
        data: Lateri3Play.LinkData,
        callback: (ExtractorLink) -> Unit
    ) {
        val tmdbId = data.id ?: return
        val url = if (data.season == null) {
            "https://moviesapi.club/movie/$tmdbId"
        } else {
            "https://moviesapi.club/tv/$tmdbId-${data.season}-${data.episode}"
        }

        try {
            val doc = app.get(url, referer = "https://pressplay.top/").document
            val iframe = doc.select("iframe").attr("src")
            
            if (iframe.isNotBlank() && ALLOWED_DOMAINS.any { iframe.contains(it, ignoreCase = true) }) {
                loadExtractor(iframe, null, null, callback)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    // --- DATA CLASSES ---

    data class WyzieSub(
        val url: String,
        val display: String?,
        val language: String?
    )

    data class OpenSubRes(
        val subtitles: List<OpenSubItem> = emptyList()
    )

    data class OpenSubItem(
        val url: String,
        val lang: String?
    )

    // RidoMovies Models
    data class RidoSearch(val data: RidoData?)
    data class RidoData(val items: List<RidoItem>?)
    data class RidoItem(val slug: String?, val title: String?, val contentable: RidoContentable?)
    data class RidoContentable(val imdbId: String?)
    
    data class RidoVideos(val data: List<RidoVideoItem>?)
    data class RidoVideoItem(val url: String?)
}
