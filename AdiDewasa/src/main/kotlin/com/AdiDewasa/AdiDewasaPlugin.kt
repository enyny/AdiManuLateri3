package com.AdiDewasa

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdiDewasaPlugin: BasePlugin() {
    override fun load() {
        // 1. Daftarkan Penyedia Film (AdiDewasa)
        registerMainAPI(AdiDewasa())
        
        // 2. Daftarkan Penyedia Subtitle Tambahan (MySubSource)
        registerSubtitleProvider(MySubSource())
    }
}
