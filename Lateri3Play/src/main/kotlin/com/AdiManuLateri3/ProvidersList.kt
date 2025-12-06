package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

// Import Source Lama (Lateri3Play)
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

// Import Source Baru (Dari Adicinemax21)tanpa Yflix
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeAdiDewasa
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeKisskh
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeAdimoviebox
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeIdlix
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidsrccc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidsrc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMapple
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidlink
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidfast
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVixsrc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeSuperembed
import com.AdiManuLateri3.Lateri3PlayExtractor.invokePlayer4U

data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: LinkData, 
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) -> Unit
)

@RequiresApi(Build.VERSION_CODES.O)
fun buildProviders(): List<Provider> {
    return listOf(
        // ================== PRIORITAS TINGGI (ADICINEMAX21 + IDLIX UPDATE) ==================
        Provider("adidewasa", "AdiDewasa (DramaFull)") { res, sub, cb ->
            invokeAdiDewasa(
                title = res.title ?: return@Provider,
                year = res.year,
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("idlix", "Idlix (Indo)") { res, sub, cb ->
            invokeIdlix(
                title = res.title,
                year = res.year,
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("kisskh", "Kisskh (Asian/Anime)") { res, sub, cb ->
            invokeKisskh(
                title = res.title ?: return@Provider,
                year = res.year,
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        },
        Provider("adimoviebox", "AdiMovieBox") { res, sub, cb ->
            invokeAdimoviebox(
                title = res.title ?: return@Provider,
                year = res.year,
                season = res.season,
                episode = res.episode,
                subtitleCallback = sub,
                callback = cb
            )
        },

        // ================== GLOBAL PROVIDERS (ADICINEMAX21 + LATERI3PLAY) ==================
        Provider("vidlink", "VidLink") { res, _, cb ->
            invokeVidlink(res.id, res.season, res.episode, cb)
        },
        Provider("vidsrccc", "VidPlay (Vidsrc.cc)") { res, sub, cb ->
            invokeVidsrccc(res.id, res.imdbId, res.season, res.episode, sub, cb)
        },
        Provider("vixsrc", "Vixsrc") { res, _, cb ->
            invokeVixsrc(res.id, res.season, res.episode, cb)
        },
        Provider("player4u", "Player4U") { res, _, cb ->
            if (!res.isAnime) {
                invokePlayer4U(res.title, res.season, res.episode, res.year, cb)
            }
        },
        Provider("vidsrc", "Vidsrc (Original)") { res, sub, cb ->
            invokeVidsrc(res.imdbId, res.season, res.episode, sub, cb)
        },
        Provider("vidfast", "VidFast") { res, sub, cb ->
            invokeVidfast(res.id, res.season, res.episode, sub, cb)
        },
        Provider("mapple", "Mapple") { res, sub, cb ->
            invokeMapple(res.id, res.season, res.episode, sub, cb)
        },
        Provider("superembed", "SuperEmbed") { res, sub, cb ->
            invokeSuperembed(res.id, res.season, res.episode, sub, cb)
        },

        // ================== STORAGE/HOSTING PROVIDERS (LATERI3PLAY LAMA) ==================
        Provider("uhdmovies", "UHD Movies") { res, sub, cb ->
            invokeUhdmovies(res.title, res.year, res.season, res.episode, cb, sub)
        },
        Provider("vegamovies", "VegaMovies") { res, sub, cb ->
            invokeVegamovies(res.title, res.year, res.season, res.episode, res.imdbId, sub, cb)
        },
        Provider("moviesmod", "MoviesMod") { res, sub, cb ->
            invokeMoviesmod(res.imdbId, res.year, res.season, res.episode, sub, cb)
        },
        Provider("multimovies", "MultiMovies") { res, sub, cb ->
            invokeMultimovies(res.title, res.season, res.episode, sub, cb)
        },
        Provider("ridomovies", "RidoMovies") { res, sub, cb ->
            invokeRidomovies(res.id, res.imdbId, res.season, res.episode, sub, cb)
        },
        Provider("moviesdrive", "MoviesDrive") { res, sub, cb ->
            invokeMoviesdrive(res.title, res.season, res.episode, res.year, res.imdbId, sub, cb)
        },
        Provider("dotmovies", "DotMovies") { res, sub, cb ->
            invokeDotmovies(res.imdbId, res.title, res.year, res.season, res.episode, sub, cb)
        },
        Provider("rogmovies", "RogMovies") { res, sub, cb ->
            invokeRogmovies(res.imdbId, res.title, res.year, res.season, res.episode, sub, cb)
        },
        Provider("hdmovie2", "HDMovie2") { res, sub, cb ->
            invokeHdmovie2(res.title, res.year, res.season, res.episode, sub, cb)
        },
        Provider("topmovies", "TopMovies") { res, sub, cb ->
            invokeTopMovies(res.imdbId, res.year, res.season, res.episode, sub, cb)
        },
        Provider("bollyflix", "BollyFlix") { res, sub, cb ->
            invokeBollyflix(res.imdbId, res.season, res.episode, sub, cb)
        }
    )
}
