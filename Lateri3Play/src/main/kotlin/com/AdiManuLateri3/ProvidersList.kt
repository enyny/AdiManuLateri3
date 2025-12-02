package com.AdiManuLateri3

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.AdiManuLateri3.Lateri3PlayExtractor.invokePrimeSrc
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeRiveStream
import com.AdiManuLateri3.Lateri3PlayExtractor.invokeUqloadsXyz

data class Provider(
    val id: String,
    val name: String,
    val invoke: suspend (
        res: Lateri3Play.LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) -> Unit
)

@RequiresApi(Build.VERSION_CODES.O)
fun buildProviders(): List<Provider> {
    return listOf(
        Provider("primesrc", "PrimeWire (PrimeSrc)") { res, subtitleCallback, callback ->
            invokePrimeSrc(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("uqloadsxyz", "UqloadsXyz") { res, subtitleCallback, callback ->
            // UqloadsXyz di sini menggunakan IMDB ID sebagai parameter (disimulasikan)
            invokeUqloadsXyz(res.imdbId, res.season, res.episode, subtitleCallback, callback)
        },
        Provider("rivestream", "RiveStream") { res, _, callback ->
            // RiveStream menggunakan TMDB ID
            invokeRiveStream(res.id, res.season, res.episode, callback)
        },
    )
}
