package com.AdiDewasa

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AdiDewasaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AdiDewasa())
        registerExtractorAPI(JeniusplayDewasa())
    }
}
