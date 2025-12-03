package com.AdiManuLateri3

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.api.Log
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
        // SharedPreferences dengan nama baru
        val sharedPref = context.getSharedPreferences("Lateri3Play", Context.MODE_PRIVATE)

        // Hanya mendaftarkan satu MainAPI utama
        registerMainAPI(Lateri3Play(sharedPref))

        // Mendaftarkan Extractor (File Host) penting yang digunakan oleh provider pilihan
        // Extractor ini nanti akan didefinisikan ulang di file Extractors.kt
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(Modflix())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(Driveleech())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(Multimovies()) // Diperlukan untuk provider MultiMovies
        registerExtractorAPI(Filelions())
        registerExtractorAPI(Ridoo())
        
        // Extractor standar CloudStream
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(StreamSB())
        registerExtractorAPI(Voe())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(VidHidePro6())

        // Menu Pengaturan
        openSettings = { ctx ->
            val act = ctx as AppCompatActivity
            if (!act.isFinishing && !act.isDestroyed) {
                val frag = MainSettingsFragment(this, sharedPref)
                frag.show(act.supportFragmentManager, "Settings")
            } else {
                Log.e("Lateri3Play", "Activity is not valid anymore, cannot show settings dialog")
            }
        }
    }
}
