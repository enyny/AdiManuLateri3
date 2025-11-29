package com.Adicinemax21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Adicinemax21Plugin : Plugin() {
    override fun load(context: Context) {
        // Register Main Provider
        registerMainAPI(Adicinemax21())
        
        // Register Existing Extractor
        registerExtractorAPI(Jeniusplay2())
        
        // Register NEW Yflix/MegaUp Extractors
        // Wajib didaftarkan agar loadExtractor("https://megaup.live/...") berfungsi
        // Extractor ini digunakan oleh fungsi invokeYflix
        registerExtractorAPI(MegaUp())
        registerExtractorAPI(Fourspromax())
        registerExtractorAPI(Rapidairmax())
        registerExtractorAPI(Rapidshare())
    }
}
