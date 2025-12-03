package com.AdiManuLateri3

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.api.Log

// Import Standard Extractors (Bawaan CloudStream)
import com.lagradost.cloudstream3.extractors.DoodYtExtractor
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.extractors.Voe

@CloudstreamPlugin
class Lateri3PlayPlugin : Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("Lateri3Play", Context.MODE_PRIVATE)

        // 1. Mendaftarkan API Utama (Otak dari ekstensi)
        registerMainAPI(Lateri3Play(sharedPref))

        // 2. Mendaftarkan Custom Extractors (Dari file Extractors.kt yang baru dibuat)
        // Extractor ini menangani situs download seperti HubCloud, GDFlix, dll.
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(Modflix())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(Ridoo())
        
        // Register Alias/Kompatibilitas
        registerExtractorAPI(Driveleech())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(Filelions())

        // 3. Mendaftarkan Standard Extractors (Untuk link video langsung)
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(StreamSB())
        registerExtractorAPI(Voe())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(VidHidePro6())

        // Konfigurasi Settings (Opsional, aktifkan jika Anda memiliki file MainSettingsFragment)
        /*
        openSettings = { ctx ->
            try {
                // Pastikan Anda mengimport MainSettingsFragment jika ingin menggunakan ini
                // val act = ctx as AppCompatActivity
                // val frag = com.AdiManuLateri3.settings.MainSettingsFragment(this, sharedPref)
                // frag.show(act.supportFragmentManager, "Settings")
            } catch (e: Exception) {
                Log.e("Lateri3Play", "Error opening settings: ${e.message}")
            }
        }
        */
    }
}
