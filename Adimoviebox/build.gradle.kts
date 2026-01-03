import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.kotlin.dsl.configure

version = 2 // Versi dinaikkan karena ada perubahan besar

cloudstream {
    description = "Nonton sampe biji mata kaluar"
    language    = "id"
    authors = listOf("AdiManuLateri3")
    status = 1
    [span_1](start_span)// Mendukung semua tipe dari Moviebox[span_1](end_span)
    tvTypes = listOf("Movie", "TvSeries", "Anime", "AsianDrama")
    iconUrl = "https://raw.githubusercontent.com/michat88/Zaneta/refs/heads/main/Icons/adi.png"
    isCrossPlatform = true
}
