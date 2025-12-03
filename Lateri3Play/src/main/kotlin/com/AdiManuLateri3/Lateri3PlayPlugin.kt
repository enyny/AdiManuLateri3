package com.AdiManuLateri3

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.api.Log
import androidx.core.content.edit

// Import Extractors umum yang stabil
import com.lagradost.cloudstream3.extractors.DoodYtExtractor
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.Mp4Upload
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Streamlare
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.VidStack

@CloudstreamPlugin
class Lateri3PlayPlugin : Plugin() {
    private val registeredMainApis = mutableListOf<MainAPI>()

    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("Lateri3Play", Context.MODE_PRIVATE)

        // Inisialisasi Provider Utama
        val mainApis = listOf(
            Lateri3Play(sharedPref)
        )

        // Logika Pengaturan (Enable/Disable Provider)
        val savedSet = sharedPref.getStringSet("enabled_plugins_saved", null)
        val defaultEnabled = mainApis.map { it.name }.toSet()
        val enabledSet = savedSet ?: defaultEnabled

        Log.d("Lateri3Play", "Enabled providers: $enabledSet")

        for (api in mainApis) {
            if (enabledSet.contains(api.name)) {
                registerMainAPI(api)
                registeredMainApis.add(api)
            }
        }

        // Bersihkan cache lama jika ada
        sharedPref.edit { remove("enabled_plugins_set") }

        // ===================== Register Extractors ========================= //
        // Mendaftarkan extractor yang diperlukan oleh 10 provider terpilih
        
        // Generic Extractors (Sering dipakai oleh RidoMovies, Moflix, ZoeChip)
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(StreamSB())
        registerExtractorAPI(Voe())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Mp4Upload())
        registerExtractorAPI(Streamlare())
        registerExtractorAPI(VidStack())

        // Custom Extractors (Yang didefinisikan di Extractors.kt nanti)
        // Pastikan nama class di Extractors.kt sesuai dengan yang dipanggil di sini
        registerExtractorAPI(Multimovies()) // Untuk MultiMovies
        registerExtractorAPI(Ridoo())       // Untuk RidoMovies
        registerExtractorAPI(GDFlix())      // Untuk UHDMovies
        registerExtractorAPI(HubCloud())    // Untuk UHDMovies
        registerExtractorAPI(PixelDrain())  // Umum
        registerExtractorAPI(Gofile())      // Umum

        // Setup Tombol Pengaturan di Halaman Plugin
        openSettings = { ctx ->
            val act = ctx as AppCompatActivity
            if (!act.isFinishing && !act.isDestroyed) {
                val frag = MainSettingsFragment(this, sharedPref)
                frag.show(act.supportFragmentManager, "Lateri3Settings")
            } else {
                Log.e("Lateri3Play", "Activity invalid, cannot show settings")
            }
        }
    }
}
