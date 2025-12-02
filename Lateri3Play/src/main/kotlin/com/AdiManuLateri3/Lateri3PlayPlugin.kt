package com.AdiManuLateri3

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.api.Log

@CloudstreamPlugin
class Lateri3PlayPlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan API Utama
        val sharedPref = context.getSharedPreferences("Lateri3Play", Context.MODE_PRIVATE)
        registerMainAPI(Lateri3Play(sharedPref))

        // Mendaftarkan Ekstraktor Standar (Uqloadsxyz)
        // PrimeWire dan RiveStream akan ditangani secara internal di dalam Lateri3PlayExtractors
        registerExtractorAPI(UqloadsXyz())
    }
}
