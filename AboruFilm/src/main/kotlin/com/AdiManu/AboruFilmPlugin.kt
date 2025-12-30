package com.AdiManu

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AboruFilmPlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan API utama AboruFilm.
        // Pastikan nama class 'AboruFilm' sesuai dengan yang ada di AboruFilm.kt
        registerMainAPI(AboruFilm())
    }
}
