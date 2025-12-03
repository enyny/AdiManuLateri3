package com.AdiManuLateri3

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

import com.AdiManuLateri3.Lateri3PlayExtractor.invokeUhdmovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMultimovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeNinetv
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRidomovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeZoechip
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeNepu
import com.AdiManuLateri3.Lateri3PlayExtractor.invokePlaydesi
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMoflix
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidsrc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWatchsomuch

data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: Lateri3Play.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) -> Unit
)

fun buildProviders(): List<Provider> {
    return listOf(
        Provider("uhdmovies", "UHD Movies") { res, sc, cb ->
            if (!res.isAnime) invokeUhdmovies(res.title, res.year, res.season, res.episode, sc, cb)
        },
        Provider("multimovies", "MultiMovies") { res, sc, cb ->
            if (!res.isAnime) invokeMultimovies(res.title, res.season, res.episode, sc, cb)
        },
        Provider("ninetv", "NineTV") { res, sc, cb ->
            if (!res.isAnime) invokeNinetv(res.id, res.season, res.episode, sc, cb)
        },
        Provider("ridomovies", "RidoMovies") { res, sc, cb ->
            if (!res.isAnime) invokeRidomovies(res.id, res.imdbId, res.season, res.episode, sc, cb)
        },
        Provider("zoechip", "ZoeChip") { res, sc, cb ->
            if (!res.isAnime) invokeZoechip(res.title, res.year, res.season, res.episode, sc, cb)
        },
        Provider("nepu", "Nepu") { res, sc, cb ->
            if (!res.isAnime) invokeNepu(res.title, res.year, res.season, res.episode, cb)
        },
        Provider("playdesi", "PlayDesi") { res, sc, cb ->
            if (!res.isAnime) invokePlaydesi(res.title, res.season, res.episode, sc, cb)
        },
        Provider("moflix", "Moflix") { res, sc, cb ->
            if (!res.isAnime) invokeMoflix(res.id, res.season, res.episode, cb)
        },
        Provider("vidsrc", "VidSrc") { res, sc, cb ->
            // UPDATE: Menambahkan parameter subtitleCallback (sc) agar sesuai definisi di Extractor
            if (!res.isAnime) invokeVidsrc(res.id, res.season, res.episode, sc, cb)
        },
        Provider("watchsomuch", "WatchSoMuch") { res, sc, cb ->
            if (!res.isAnime) invokeWatchsomuch(res.imdbId, res.season, res.episode, sc)
        }
    )
}
