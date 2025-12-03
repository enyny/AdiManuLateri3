package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeBollyflix
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeDotmovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeHdmovie2
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMoviesdrive
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMoviesmod
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMultimovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRidomovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRogmovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeTopMovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeUhdmovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVegamovies

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
        // 1. UHD Movies
        Provider("uhdmovies", "UHD Movies") { res, subtitleCallback, callback ->
            invokeUhdmovies(res.title, res.year, res.season, res.episode, callback, subtitleCallback)
        },
        
        // 2. VegaMovies
        Provider("vegamovies", "VegaMovies") { res, subtitleCallback, callback ->
            invokeVegamovies(res.title, res.year, res.season, res.episode, res.imdbId, subtitleCallback, callback)
        },
        
        // 3. MoviesMod
        Provider("moviesmod", "MoviesMod") { res, subtitleCallback, callback ->
            invokeMoviesmod(res.imdbId, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 4. MultiMovies
        Provider("multimovies", "MultiMovies") { res, subtitleCallback, callback ->
            invokeMultimovies(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 5. RidoMovies
        Provider("ridomovies", "RidoMovies") { res, subtitleCallback, callback ->
            invokeRidomovies(res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 6. MoviesDrive
        Provider("moviesdrive", "MoviesDrive") { res, subtitleCallback, callback ->
            invokeMoviesdrive(res.title, res.season, res.episode, res.year, res.imdbId, subtitleCallback, callback)
        },
        
        // 7. DotMovies
        Provider("dotmovies", "DotMovies") { res, subtitleCallback, callback ->
            invokeDotmovies(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 8. RogMovies
        Provider("rogmovies", "RogMovies") { res, subtitleCallback, callback ->
            invokeRogmovies(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 9. HDMovie2
        Provider("hdmovie2", "Hdmovie2") { res, subtitleCallback, callback ->
            invokeHdmovie2(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 10. TopMovies
        Provider("topmovies", "Top Movies") { res, subtitleCallback, callback ->
            invokeTopMovies(res.imdbId, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        
        // Tambahan (Opsional/Cadangan dari source code asli yang bagus)
        Provider("bollyflix", "Bollyflix") { res, subtitleCallback, callback ->
            invokeBollyflix(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        }
    )
}
