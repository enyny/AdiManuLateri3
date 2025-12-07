package com.AdiFilmSemi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AdiFilmSemiPlugin : Plugin() {
    override fun load(context: Context) {
        // Register Main Provider
        registerMainAPI(AdiFilmSemi())
        
        // Register Extractors
        // Kita hanya butuh Jeniusplay yang baru, karena Yflix sudah dihapus
        registerExtractorAPI(Jeniusplay())
    }
}
