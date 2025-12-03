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

class MainSettingsFragment(
    private val plugin: Lateri3PlayPlugin,
    private val sharedPref: android.content.SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    // FIX: Menggunakan string literal langsung untuk package name
    private fun getResId(name: String, type: String): Int {
        val packageName = "com.AdiManuLateri3"
        return res.getIdentifier(name, type, packageName)
    }

    private fun getDrawable(name: String): Drawable? {
        val id = getResId(name, "drawable")
        return if (id != 0) res.getDrawable(id, null) else null
    }

    private fun <T : View> View.findView(name: String): T {
        val id = getResId(name, "id")
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = getResId(name, "layout")
        return inflater.inflate(id, container, false)
    }

    private fun View.makeTvCompatible() {
        val outlineId = getResId("outline", "drawable")
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

        val loginCard: ImageView = view.findView("loginCard")
        val featureCard: ImageView = view.findView("featureCard")
        val toggleproviders: ImageView = view.findView("toggleproviders") 
        val languagechange: ImageView = view.findView("languageCard") 
        val saveIcon: ImageView = view.findView("saveIcon")

        loginCard.setImageDrawable(getDrawable("settings_icon"))
        languagechange.setImageDrawable(getDrawable("settings_icon"))
        featureCard.setImageDrawable(getDrawable("settings_icon"))
        toggleproviders.setImageDrawable(getDrawable("settings_icon"))
        saveIcon.setImageDrawable(getDrawable("save_icon"))

        loginCard.makeTvCompatible()
        featureCard.makeTvCompatible()
        toggleproviders.makeTvCompatible()
        languagechange.makeTvCompatible()
        saveIcon.makeTvCompatible()

        // Sembunyikan menu yang tidak terpakai (Login & Extension Toggle)
        (loginCard.parent.parent as? View)?.visibility = View.GONE
        (featureCard.parent.parent as? View)?.visibility = View.GONE

        // Tombol Pilih Source
        toggleproviders.setOnClickListener {
            val providersFragment = ProvidersFragment(plugin, sharedPref)
            providersFragment.show(
                activity?.supportFragmentManager ?: return@setOnClickListener,
                "fragment_toggle_providers"
            )
        }

        // Tombol Ganti Bahasa
        languagechange.setOnClickListener {
            val langFragment = LanguageSelectFragment(plugin, sharedPref)
            langFragment.show(
                activity?.supportFragmentManager ?: return@setOnClickListener,
                "fragment_language_list"
            )
        }

        // Tombol Simpan & Restart
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
