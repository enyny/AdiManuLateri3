package com.Adimoviebo

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AdimovieboxProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Adimoviebox())
    }
}
