package com.JavHey

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JavHeyPlugin: Plugin() {
    override fun load(context: Context) {
        // 1. Daftarkan Provider Utama
        registerMainAPI(JavHey())

        // 2. Daftarkan Extractor Sesuai Screenshot & Permintaan

        // --- VidHidePro (Sesuai Screenshot) ---
        registerExtractorAPI(VidHidePro1())
        registerExtractorAPI(VidHidePro2())
        registerExtractorAPI(VidHidePro3())
        registerExtractorAPI(VidHidePro4())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(VidHidePro6())

        // --- EarnVids (Sesuai Screenshot) ---
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(Dhtpre())
        registerExtractorAPI(Peytonepre())

        // --- MixDrop (Sesuai Screenshot) ---
        registerExtractorAPI(MixDropBz())
        registerExtractorAPI(MixDropAg())
        registerExtractorAPI(MixDropCh())
        registerExtractorAPI(MixDropTo())

        // --- Streamwish (Sesuai Screenshot) ---
        registerExtractorAPI(Mwish())
        registerExtractorAPI(Dwish())
        registerExtractorAPI(Streamwish2())
        registerExtractorAPI(WishembedPro())
        registerExtractorAPI(Wishfast())
        
        // --- Swdyu (Sesuai Screenshot) ---
        registerExtractorAPI(Swdyu())

        // --- DoodStream (Sesuai Screenshot) ---
        registerExtractorAPI(D0000d())
        registerExtractorAPI(DoodstreamCom())
        registerExtractorAPI(Dooood())
        registerExtractorAPI(DoodWfExtractor())
        registerExtractorAPI(DoodCxExtractor())
        registerExtractorAPI(DoodShExtractor())
        registerExtractorAPI(DoodWatchExtractor())
        registerExtractorAPI(DoodPmExtractor())
        registerExtractorAPI(DoodToExtractor())
        registerExtractorAPI(DoodSoExtractor())
        registerExtractorAPI(DoodWsExtractor())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(DoodLiExtractor())
        registerExtractorAPI(Ds2play())
        registerExtractorAPI(Ds2video())
        registerExtractorAPI(MyVidPlay())

        // --- LuluStream (Request Tambahan) ---
        registerExtractorAPI(Lulustream1())
        registerExtractorAPI(Lulustream2())
    }
}
