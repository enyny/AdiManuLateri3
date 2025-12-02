package com.AdiManuLateri3

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.api.Log

@CloudstreamPlugin
class Lateri3PlayPlugin: Plugin() {

    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("Lateri3Play", Context.MODE_PRIVATE)

        // HANYA daftarkan MainAPI yang diperlukan (Lateri3Play)
        registerMainAPI(Lateri3Play(sharedPref))
        Log.d("Lateri3Play", "Registered plugin: Lateri3Play")

        // Hapus SEMUA extractor pihak ketiga (hanya menggunakan yang disederhanakan)
        // Extractors yang digunakan akan diimplementasikan secara langsung di StreamPlayExtractor/StreamPlayUtils
        
        // Buka menu pengaturan
        openSettings = { ctx ->
            val act = ctx as AppCompatActivity
            if (!act.isFinishing && !act.isDestroyed) {
                val frag = MainSettingsFragment(this, sharedPref)
                frag.show(act.supportFragmentManager, "Frag")
            } else {
                Log.e("Plugin", "Activity is not valid anymore, cannot show settings dialog")
            }
        }
    }
}
