package com.AdiManuLateri3

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

// Import fungsi dari Extractor yang akan kita buat di file berikutnya
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
        // 1. UHDMovies (High Quality - GDrive/Index)
        Provider("uhdmovies", "UHD Movies") { res, sc, cb ->
            if (!res.isAnime) invokeUhdmovies(res.title, res.year, res.season, res.episode, sc, cb)
        },

        // 2. MultiMovies (Stable Scraping)
        Provider("multimovies", "MultiMovies") { res, sc, cb ->
            if (!res.isAnime) invokeMultimovies(res.title, res.season, res.episode, sc, cb)
        },

        // 3. NineTV (Simple Iframe)
        Provider("ninetv", "NineTV") { res, sc, cb ->
            if (!res.isAnime) invokeNinetv(res.id, res.season, res.episode, sc, cb)
        },

        // 4. RidoMovies (Good for Movies)
        Provider("ridomovies", "RidoMovies") { res, sc, cb ->
            if (!res.isAnime) invokeRidomovies(res.id, res.imdbId, res.season, res.episode, sc, cb)
        },

        // 5. ZoeChip (Popular)
        Provider("zoechip", "ZoeChip") { res, sc, cb ->
            if (!res.isAnime) invokeZoechip(res.title, res.year, res.season, res.episode, cb)
        },

        // 6. Nepu (AJAX based)
        Provider("nepu", "Nepu") { res, sc, cb ->
            if (!res.isAnime) invokeNepu(res.title, res.year, res.season, res.episode, cb)
        },

        // 7. PlayDesi (Regional/General)
        Provider("playdesi", "PlayDesi") { res, sc, cb ->
            if (!res.isAnime) invokePlaydesi(res.title, res.season, res.episode, sc, cb)
        },

        // 8. Moflix (Aggregator)
        Provider("moflix", "Moflix") { res, sc, cb ->
            if (!res.isAnime) invokeMoflix(res.id, res.season, res.episode, cb)
        },

        // 9. Vidsrc (Basic Backup)
        Provider("vidsrc", "VidSrc") { res, sc, cb ->
            if (!res.isAnime) invokeVidsrc(res.id, res.season, res.episode, cb)
        },

        // 10. WatchSoMuch (Subs/Direct Links)
        Provider("watchsomuch", "WatchSoMuch") { res, sc, cb ->
            if (!res.isAnime) invokeWatchsomuch(res.imdbId, res.season, res.episode, sc)
        }
    )
}
