package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdiManuLateri3.Lateri3PlayExtractor.invoke4khdhub
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeDramadrip
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeHdhub4u
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeKisskh
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeKisskhAsia
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMovieBox
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMultiEmbed
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeMultimovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokePlayer4U
import com.AdiManuLateri3.Lateri3PlayExtractor.invokePrimeSrc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRidomovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeTopMovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeUhdmovies
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidFast
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidSrcXyz
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidlink
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidrock
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidsrccc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeVidzee
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWatch32APIHQ
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeWatchsomuch

// Mendefinisikan struktur Provider
data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: Lateri3Play.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        token: String,
        extraParam: String // Placeholder jika diperlukan di masa depan
    ) -> Unit
)

@RequiresApi(Build.VERSION_CODES.O)
fun buildProviders(): List<Provider> {
    return listOf(
        // 1. UHD Movies
        Provider("uhdmovies", "UHD Movies") { res, subtitleCallback, callback, _, _ ->
            invokeUhdmovies(res.title, res.year, res.season, res.episode, callback, subtitleCallback)
        },
        
        // 2. Player4U
        Provider("player4u", "Player4U") { res, _, callback, _, _ ->
            invokePlayer4U(res.title, res.season, res.episode, res.year, callback)
        },
        
        // 3. Vidsrccc
        Provider("vidsrccc", "Vidsrccc") { res, _, callback, _, _ ->
            invokeVidsrccc(res.id, res.season, res.episode, callback)
        },
        
        // 4. Top Movies
        Provider("topmovies", "Top Movies") { res, subtitleCallback, callback, _, _ ->
            invokeTopMovies(res.imdbId, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 5. RidoMovies
        Provider("ridomovies", "RidoMovies") { res, subtitleCallback, callback, _, _ ->
            invokeRidomovies(res.id, res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 6. MultiEmbed
        Provider("multiembed", "MultiEmbed") { res, _, callback, _, _ ->
            invokeMultiEmbed(res.imdbId, res.season, res.episode, callback)
        },
        
        // 7. MultiMovies
        Provider("multimovies", "MultiMovies") { res, subtitleCallback, callback, _, _ ->
            invokeMultimovies(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 8. Watch32
        Provider("watch32", "Watch32") { res, subtitleCallback, callback, _, _ ->
            invokeWatch32APIHQ(res.title, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 9. PrimeSrc
        Provider("primesrc", "PrimeSrc") { res, subtitleCallback, callback, _, _ ->
            invokePrimeSrc(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 10. VidSrcXyz
        Provider("vidsrcxyz", "VidSrcXyz") { res, _, callback, _, _ ->
            invokeVidSrcXyz(res.imdbId, res.season, res.episode, callback)
        },
        
        // 11. Vidzee
        Provider("vidzee", "Vidzee") { res, subtitleCallback, callback, _, _ ->
            invokeVidzee(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 12. 4kHdhub
        Provider("4khdhub", "4kHdhub") { res, subtitleCallback, callback, _, _ ->
            invoke4khdhub(res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 13. Hdhub4u
        Provider("hdhub4u", "Hdhub4u") { res, subtitleCallback, callback, _, _ ->
            invokeHdhub4u(res.imdbId, res.title, res.year, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 14. Dramadrip
        Provider("dramadrip", "Dramadrip") { res, subtitleCallback, callback, _, _ ->
            invokeDramadrip(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 15. Vidrock
        Provider("vidrock", "Vidrock") { res, _, callback, _, _ ->
            invokeVidrock(res.id, res.season, res.episode, callback)
        },
        
        // 16. Vidlink
        Provider("vidlink", "Vidlink") { res, subtitleCallback, callback, _, _ ->
            invokeVidlink(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 17. KissKH
        Provider("kisskh", "KissKH") { res, subtitleCallback, callback, _, _ ->
            // Note: lastSeason tidak selalu tersedia di LinkData standar, kita gunakan null/0
            invokeKisskh(res.title, res.season, res.episode, null, subtitleCallback, callback)
        },
        
        // 18. KissKhAsia
        Provider("kisskhasia", "KissKhAsia") { res, subtitleCallback, callback, _, _ ->
            invokeKisskhAsia(res.id, res.season, res.episode, subtitleCallback, callback)
        },
        
        // 19. VidFast
        Provider("vidfast", "VidFast") { res, _, callback, _, _ ->
            invokeVidFast(res.id, res.season, res.episode, callback)
        },
        
        // 20. WatchSoMuch
        Provider("watchsomuch", "WatchSoMuch") { res, subtitleCallback, _, _, _ ->
            invokeWatchsomuch(res.imdbId, res.season, res.episode, subtitleCallback)
        },
        
        // 21. MovieBox
        Provider("moviebox", "MovieBox") { res, subtitleCallback, callback, _, _ ->
            invokeMovieBox(res.title, res.season, res.episode, subtitleCallback, callback)
        }
    )
}
