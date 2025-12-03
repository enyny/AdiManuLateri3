package com.AdiManuLateri3

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.api.Log

// Import Standard Extractors
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

        // 1. Mendaftarkan API Utama
        registerMainAPI(Lateri3Play(sharedPref))

        // 2. Mendaftarkan Custom Extractors
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(Modflix())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(Ridoo())
        
        // Register Alias
        registerExtractorAPI(Driveleech())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(Filelions())

        // 3. Mendaftarkan Standard Extractors
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(StreamSB())
        registerExtractorAPI(Voe())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(VidHidePro6())

        // 4. Konfigurasi Settings (SUDAH DIAKTIFKAN KEMBALI)
        openSettings = { ctx ->
            try {
                val act = ctx as AppCompatActivity
                // Memanggil MainSettingsFragment yang ada di package yang sama
                val frag = MainSettingsFragment(this, sharedPref)
                frag.show(act.supportFragmentManager, "Settings")
            } catch (e: Exception) {
                Log.e("Lateri3Play", "Error opening settings: ${e.message}")
            }
        }
    }
}
