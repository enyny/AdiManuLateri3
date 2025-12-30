package com.AdiManu

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AboruFilmPlugin: Plugin() {
    override fun load(context: Context) {
        // Kita menginisialisasi API utama (AboruFilm).
        // Tidak perlu passing 'sharedPreferences' atau 'settings' karena token sudah di-hardcode.
        val api = AboruFilm() 
        registerMainAPI(api)
    }
}
