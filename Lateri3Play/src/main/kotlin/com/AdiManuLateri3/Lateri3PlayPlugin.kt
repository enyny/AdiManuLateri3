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
import com.AdiManuLateri3.settings.MainSettingsFragment // ✅ Import Ditambahkan

@CloudstreamPlugin
class Lateri3PlayPlugin : Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("Lateri3Play", Context.MODE_PRIVATE)

        // MainAPI
        registerMainAPI(Lateri3Play(sharedPref))

        // File Hosts
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(Modflix())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(Driveleech())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(Ridoo())
        
        // Standard Extractors
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(StreamSB())
        registerExtractorAPI(Voe())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(VidHidePro6())

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
