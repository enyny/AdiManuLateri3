package com.AdiManuLateri3

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Lateri3PlayPlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan Provider Utama
        registerMainAPI(Lateri3Play())
        
        // Kita tidak perlu mendaftarkan ratusan extractor custom yang tidak ada kodenya.
        // Extractor umum (Dood, MixDrop, dll) sudah ditangani oleh Cloudstream atau 
        // dipanggil langsung di Lateri3PlayExtractor.kt.
    }
}
