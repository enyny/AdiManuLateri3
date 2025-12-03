package com.AdiManuLateri3.settings

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.AdiManuLateri3.Lateri3PlayPlugin
// HAPUS IMPORT BuildConfig JIKA ADA MASALAH, KITA GUNAKAN CONTEXT

class MainSettingsFragment(
    private val plugin: Lateri3PlayPlugin,
    private val sharedPref: android.content.SharedPreferences
) : BottomSheetDialogFragment() {

    // Mengambil resources langsung dari plugin
    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")
    
    // TRICK: Mengambil package name resource yang BENAR dari salah satu resource yang pasti ada
    // Namun karena kita tidak punya R class, kita gunakan hardcoded package name yang HARUS SAMA dengan namespace di build.gradle
    private val packageName = "com.AdiManuLateri3" 

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) throw Exception("Drawable $name not found in package $packageName")
        return res.getDrawable(id, null) ?: throw Exception("Drawable $name cannot be loaded")
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", packageName)
        if (id == 0) throw Exception("View ID $name not found in package $packageName")
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        try {
            val outlineId = res.getIdentifier("outline", "drawable", packageName)
            if (outlineId != 0) {
                this.background = res.getDrawable(outlineId, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", packageName)
        if (id == 0) throw Exception("Layout $name not found in package $packageName. Cek apakah file XML ada dan nama paket benar.")
        // PENTING: Gunakan layout inflater dari konteks plugin, bukan dari fragment host
        // Tapi karena fragment attached ke host, kita harus inflate menggunakan ID resource dari plugin
        // Triknya adalah memastikan 'res' yang digunakan untuk inflate adalah 'res' plugin
        
        // Inflater standar (inflater.inflate) menggunakan resources aplikasi utama (CloudStream)
        // Kita harus menggunakan XmlResourceParser dari plugin resources
        val parser = res.getLayout(id)
        return inflater.inflate(parser, container, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Coba inflate
        val view = getLayout("fragment_main_settings", inflater, container)

        // Binding Views
        val toggleproviders: ImageView = view.findView("toggleproviders")
        val saveIcon: ImageView = view.findView("saveIcon")

        toggleproviders.setImageDrawable(getDrawable("settings_icon"))
        saveIcon.setImageDrawable(getDrawable("save_icon"))

        toggleproviders.makeTvCompatible()
        saveIcon.makeTvCompatible()

        toggleproviders.setOnClickListener {
            val providersFragment = ProvidersFragment(plugin, sharedPref)
            providersFragment.show(
                activity?.supportFragmentManager ?: throw Exception("No FragmentManager"),
                "fragment_toggle_providers"
            )
        }

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
