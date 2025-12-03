package com.AdiManuLateri3

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.DoodYtExtractor
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.Mp4Upload
import com.lagradost.cloudstream3.extractors.OkRu
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.Voe

@CloudstreamPlugin
class Lateri3PlayPlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan API Utama
        registerMainAPI(Lateri3Play())

        // Mendaftarkan 10 Extractor Pilihan (Tanpa Secret Keys rumit)
        // 1. StreamWish
        registerExtractorAPI(StreamWishExtractor())
        // 2. FileMoon
        registerExtractorAPI(FileMoon())
        // 3. Mp4Upload
        registerExtractorAPI(Mp4Upload())
        // 4. DoodStream / DoodYT
        registerExtractorAPI(DoodYtExtractor())
        // 5. MixDrop
        registerExtractorAPI(MixDrop())
        // 6. StreamTape
        registerExtractorAPI(StreamTape())
        // 7. Voe
        registerExtractorAPI(Voe())
        // 8. VidHide
        registerExtractorAPI(VidHidePro())
        // 9. OkRu
        registerExtractorAPI(OkRu())
        // 10. StreamSB
        registerExtractorAPI(StreamSB())
    }
}
