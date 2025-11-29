package com.AdiFilmSemi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AdiFilmSemiPlugin : Plugin() {
    override fun load(context: Context) {
        // Register Main Provider
        registerMainAPI(AdiFilmSemi())
        
        // Register Existing Extractor
        registerExtractorAPI(Jeniusplay2())
        
        // Register NEW Yflix/MegaUp Extractors
        // Wajib didaftarkan agar fungsi Yflix berjalan lancar
        registerExtractorAPI(MegaUp())
        registerExtractorAPI(Fourspromax())
        registerExtractorAPI(Rapidairmax())
        registerExtractorAPI(Rapidshare())
    }
}
