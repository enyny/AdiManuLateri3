package com.AdiManu

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AboruFilmPlugin: Plugin() {
    override fun load(context: Context) {
        [span_3](start_span)// Mendaftarkan API utama AboruFilm ke aplikasi Cloudstream.[span_3](end_span)
        // Berbeda dengan file asli Superstream, kita memanggil AboruFilm() secara langsung
        [span_4](start_span)// karena semua rahasia (token & sertifikat) sudah ada di dalam kelas tersebut.[span_4](end_span)
        val api = AboruFilm() 
        registerMainAPI(api)
    }
}
