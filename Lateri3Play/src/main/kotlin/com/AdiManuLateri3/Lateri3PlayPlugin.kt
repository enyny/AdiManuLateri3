package com.AdiManuLateri3

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Lateri3PlayPlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan Provider Utama
        registerMainAPI(Lateri3Play())

        // Mendaftarkan Extractor Tambahan (dari file Extractors.kt)
        // Ini memungkinkan loadExtractor mengenali URL dari domain ini secara otomatis
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(Gofile())
    }
}
