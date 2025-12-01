package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

// Import fungsi-fungsi dari Lateri3PlayExtractor
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMultiEmbed
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMultimovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeKisskh
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeKisskhAsia
import com.AdiManuLateri3.Lateri3PlayExtractor.invokePlayer4U
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidsrccc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeTopMovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRidomovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWatch32APIHQ
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidSrcXyz
import com.AdiManuLateri3.Lateri3PlayExtractor.invokePrimeSrc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidzee
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeUhdmovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMovieBox
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidFast
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeDramadrip
import com.AdiManuLateri3.Lateri3PlayExtractor.invoke4khdhub
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeHdhub4u
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidrock
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidlink
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWatchsomuch

// Definisi Data Class Provider
data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        token: String,
        dahmerMoviesAPI: String
    ) -> Unit
)

@RequiresApi(Build.VERSION_CODES.O)
fun buildProviders(): List<Provider> {
    return listOf(
        Provider("multiembed", "MultiEmbed") { res, _, callback, _, _ ->
            invokeMultiEmbed(res.imdbId, res.season, res.episode, callback)
        },
        Provider("multimovies", "MultiMovies") { res, subtitleCallback, callback, _, _ ->
            invokeMultimovies(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("kisskh", "KissKH") { res, subtitleCallback, callback, _, _ ->
            invokeKisskh(res.title, res.season, res.episode, res.lastSeason, subtitleCallback, callback)
        },
        Provider("kisskhasia", "KissKH Asia") { res, subtitleCallback, callback, _, _ ->
            invokeKisskhAsia(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("player4u", "Player4U") { res, _, callback, _, _ ->
            invokePlayer4U(res.title, res.season, res.episode, res.year, callback)
        },
        Provider("vidsrccc", "Vidsrccc") { res, _, callback, _, _ ->
            invokeVidsrccc(res.id, res.season, res.episode, callback)
        },
        Provider("topmovies", "Top Movies") { res, subtitleCallback, callback, _, _ ->
            invokeTopMovies(res.imdbId, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("ridomovies", "RidoMovies") { res, subtitleCallback, callback, _, _ ->
            invokeRidomovies(res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("watch32", "Watch32") { res, subtitleCallback, callback, _, _ ->
            invokeWatch32APIHQ(res.title, res.season, res.episode, res.year, subtitleCallback, callback)
        },
        Provider("vidsrcxyz", "VidSrcXyz") { res, _, callback, _, _ ->
            invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback)
        },
        Provider("primesrc", "PrimeSrc") { res, subtitleCallback, callback, _, _ ->
            invokePrimeSrc(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidzee", "VidZee") { res, subtitleCallback, callback, _, _ ->
            invokeVidzee(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("uhdmovies", "UHDMovies") { res, subtitleCallback, callback, _, _ ->
            invokeUhdmovies(res.title, res.year, res.season, res.episode, callback, subtitleCallback)
        },
        Provider("moviebox", "MovieBox") { res, subtitleCallback, callback, _, _ ->
            invokeMovieBox(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidfast", "VidFast") { res, subtitleCallback, callback, _, _ ->
            // FIX: Menambahkan parameter subtitleCallback yang sebelumnya hilang
            invokeVidFast(res.id, res.season, res.episode, callback, subtitleCallback)
        },
        Provider("dramadrip", "DramaDrip") { res, subtitleCallback, callback, _, _ ->
            invokeDramadrip(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("4khdhub", "4kHdhub") { res, subtitleCallback, callback, _, _ ->
            invoke4khdhub(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("hdhub4u", "Hdhub4u") { res, subtitleCallback, callback, _, _ ->
            invokeHdhub4u(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("vidrock", "Vidrock") { res, _, callback, _, _ ->
            invokeVidrock(res.id, res.season, res.episode, callback)
        },
        Provider("vidlink", "Vidlink") { res, subtitleCallback, callback, _, _ ->
            invokeVidlink(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("watchsomuch", "WatchSoMuch") { res, subtitleCallback, _, _, _ ->
            invokeWatchsomuch(res.imdbId, res.season, res.episode, subtitleCallback)
        }
    )
}
