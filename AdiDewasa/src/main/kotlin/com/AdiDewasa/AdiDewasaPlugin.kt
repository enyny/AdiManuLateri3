package com.AdiDewasa // Diubah dari com.dramafull

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdiDewasaPlugin: BasePlugin() { // Diubah dari DramaFullPlugin
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AdiDewasa()) // Diubah dari DramaFull()
    }
}
