package com.AdiManu

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AboruFilmPlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan API utama AboruFilm ke dalam sistem Cloudstream.
        // Pastikan tidak ada karakter aneh seperti 'span' atau 'start_span' di sini.
        registerMainAPI(AboruFilm())
    }
}
