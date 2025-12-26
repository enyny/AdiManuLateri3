package com.AdiManu

import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKacaProvider())
        
        // Extractor Bawaan Cloudstream yang mungkin berguna
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(VidHidePro6())
        
        // Extractor Custom kita
        registerExtractorAPI(Furher())
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(Co4nxtrl())
        
        // TAMBAHAN BARU SESUAI LOG
        registerExtractorAPI(F16px())
        registerExtractorAPI(ShortIcu())
    }
}
