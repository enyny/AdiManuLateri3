package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

// Import fungsi-fungsi dari Extractor
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeUhdmovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVegamovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMoviesmod
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMultimovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRidomovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMoviesdrive
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeDotmovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRogmovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeHdmovie2
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeTopMovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeBollyflix

// Definisi Data Class Provider
data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: Lateri3Play.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) -> Unit
)

@RequiresApi(Build.VERSION_CODES.O)
fun buildProviders(): List<Provider> {
    return listOf(
        Provider("uhdmovies", "UHD Movies") { res, sub, cb ->
            invokeUhdmovies(
                title = res.title,
                year = res.year,
                season = res.season,
                episode = res.episode,
                callback = cb,
                subtitleCallback = sub
            )
        },
        Provider("vegamovies", "VegaMovies") { res, sub, cb ->
            invokeVegamovies(
                title = res.title,
                year = res.year,
                season = res.season,
                episode = res.episode,
                imdbId = res.imdbId,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("moviesmod", "MoviesMod") { res, sub, cb ->
            invokeMoviesmod(
                imdbId = res.imdbId,
                year = res.year,
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("multimovies", "MultiMovies") { res, sub, cb ->
            invokeMultimovies(
                title = res.title,
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("ridomovies", "RidoMovies") { res, sub, cb ->
            invokeRidomovies(
                tmdbId = res.id,
                imdbId = res.imdbId,
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("moviesdrive", "MoviesDrive") { res, sub, cb ->
            invokeMoviesdrive(
                title = res.title,
                season = res.season,
                episode = res.episode,
                year = res.year,
                imdbId = res.imdbId,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("dotmovies", "DotMovies") { res, sub, cb ->
            invokeDotmovies(
                imdbId = res.imdbId,
                title = res.title,
                year = res.year,
                season = res.season,
                episode = res.episode,
                sub = sub,
                cb = cb
            )
        },
        Provider("rogmovies", "RogMovies") { res, sub, cb ->
            invokeRogmovies(
                imdbId = res.imdbId,
                title = res.title,
                year = res.year,
                season = res.season,
                episode = res.episode,
                sub = sub,
                cb = cb
            )
        },
        Provider("hdmovie2", "HDMovie2") { res, sub, cb ->
            invokeHdmovie2(
                title = res.title,
                year = res.year,
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("topmovies", "TopMovies") { res, sub, cb ->
            invokeTopMovies(
                imdbId = res.imdbId,
                year = res.year,
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("bollyflix", "BollyFlix") { res, sub, cb ->
            invokeBollyflix(
                id = res.imdbId, // Bollyflix biasanya butuh IMDB ID atau Query
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        }
    )
}
