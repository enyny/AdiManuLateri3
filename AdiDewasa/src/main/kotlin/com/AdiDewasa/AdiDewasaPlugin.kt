package com.AdiDewasa

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdiDewasaPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(AdiDewasa())
    }
}
