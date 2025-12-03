package com.AdiManuLateri3

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MainSettingsFragment(
    private val plugin: Lateri3PlayPlugin,
    private val sharedPref: android.content.SharedPreferences
) : BottomSheetDialogFragment() {

    // Helper untuk mengambil Resource ID secara dinamis (Wajib untuk Plugin)
    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    private fun getDrawable(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return if (id != 0) res.getDrawable(id, null) else null
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    // Menambahkan efek border putih saat tombol dipilih (Penting untuk Android TV)
    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (outlineId != 0) {
            this.background = res.getDrawable(outlineId, null)
            this.isFocusable = true
            this.isClickable = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = getLayout("fragment_main_settings", inflater, container)

        // Binding Views dari XML
        val loginCard: ImageView = view.findView("loginCard") // Akan disembunyikan
        val featureCard: ImageView = view.findView("featureCard") // Akan disembunyikan
        val toggleproviders: ImageView = view.findView("toggleproviders") // PENTING
        val languagechange: ImageView = view.findView("languageCard") // PENTING
        val saveIcon: ImageView = view.findView("saveIcon")

        // Set Icons
        loginCard.setImageDrawable(getDrawable("settings_icon"))
        languagechange.setImageDrawable(getDrawable("settings_icon"))
        featureCard.setImageDrawable(getDrawable("settings_icon"))
        toggleproviders.setImageDrawable(getDrawable("settings_icon"))
        saveIcon.setImageDrawable(getDrawable("save_icon"))

        // Apply TV Styles
        loginCard.makeTvCompatible()
        featureCard.makeTvCompatible()
        toggleproviders.makeTvCompatible()
        languagechange.makeTvCompatible()
        saveIcon.makeTvCompatible()

        // --- Logika Menu ---

        // 1. Login (Febbox/Token) - Dinonaktifkan di versi Lite
        // Kita sembunyikan parent layout-nya agar UI lebih bersih
        (loginCard.parent.parent as? View)?.visibility = View.GONE

        // 2. Toggle Extensions (StreamPlay vs Lite) - Dinonaktifkan karena Single Provider
        (featureCard.parent.parent as? View)?.visibility = View.GONE

        // 3. Enable/Disable Sources (Memilih 10 Provider)
        toggleproviders.setOnClickListener {
            val providersFragment = ProvidersFragment(plugin, sharedPref)
            providersFragment.show(
                activity?.supportFragmentManager ?: return@setOnClickListener,
                "fragment_toggle_providers"
            )
        }

        // 4. Change Language (TMDb)
        languagechange.setOnClickListener {
            val langFragment = LanguageSelectFragment(plugin, sharedPref)
            langFragment.show(
                activity?.supportFragmentManager ?: return@setOnClickListener,
                "fragment_language_list"
            )
        }

        // 5. Save & Restart Button
        saveIcon.setOnClickListener {
            val context = this.context ?: return@setOnClickListener
            AlertDialog.Builder(context)
                .setTitle("Restart Aplikasi")
                .setMessage("Apakah Anda ingin me-restart aplikasi untuk menerapkan perubahan?")
                .setPositiveButton("Ya") { _, _ ->
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("Tidak", null)
                .show()
        }

        return view
    }

    private fun restartApp() {
        val context = requireContext().applicationContext
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component

        if (componentName != null) {
            val restartIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(restartIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
