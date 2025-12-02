package com.AdiManuLateri3

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

// PENTING: Import ini mengatasi error BuildConfig
import com.AdiManuLateri3.BuildConfig

class MainSettingsFragment(
    private val plugin: Lateri3PlayPlugin,
    private val sharedPref: android.content.SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null) ?: throw Exception("Drawable $name not found")
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (outlineId != 0) {
            this.background = res.getDrawable(outlineId, null)
        }
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) throw Exception("Layout $name not found in ${BuildConfig.LIBRARY_PACKAGE_NAME}")
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = getLayout("fragment_main_settings", inflater, container)

        val toggleproviders: ImageView = view.findView("providersIcon")
        val languagechange: ImageView = view.findView("languageIcon")
        val saveIcon: ImageView = view.findView("saveIcon")

        // Setel ikon (menggunakan settings_icon sebagai placeholder jika ikon spesifik tidak ada)
        languagechange.setImageDrawable(getDrawable("settings_icon"))
        toggleproviders.setImageDrawable(getDrawable("settings_icon"))
        saveIcon.setImageDrawable(getDrawable("save_icon"))

        // Tambahkan efek visual untuk navigasi TV
        languagechange.makeTvCompatible()
        toggleproviders.makeTvCompatible()
        saveIcon.makeTvCompatible()

        // Navigasi ke fragment pemilihan bahasa
        languagechange.setOnClickListener {
            LanguageSelectFragment(plugin, sharedPref).show(
                activity?.supportFragmentManager!!,
                "fragment_language_list"
            )
        }

        // Navigasi ke fragment pengaturan provider (PrimeWire, Uqloads, RiveStream)
        toggleproviders.setOnClickListener {
            val providersFragment = ProvidersFragment(plugin, sharedPref)
            providersFragment.show(
                activity?.supportFragmentManager ?: throw Exception("No FragmentManager"),
                "fragment_providers"
            )
        }

        // Tombol Save & Reload
        saveIcon.setOnClickListener {
            val context = this.context ?: return@setOnClickListener

            AlertDialog.Builder(context)
                .setTitle("Save & Reload")
                .setMessage("Changes have been saved. Do you want to restart the app to apply them?")
                .setPositiveButton("Yes") { _, _ ->
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No", null)
                .show()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {}

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
