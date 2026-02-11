package com.LayarKacaProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class LayarKacaPlugin : Plugin() {
    override fun load(context: Context) {
        // Provider Utama
        registerMainAPI(LayarKacaProvider())
        
        // Extractor Servers
        registerExtractorAPI(P2PExtractor())        // P2P
        registerExtractorAPI(F16Extractor())        // CAST (Baru!)
    }
}
