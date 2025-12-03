package com.AdiManuLateri3

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.view.isNotEmpty
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

private const val PREFS_PROFILES = "provider_profiles"
private const val PREFS_DISABLED = "disabled_providers"

class ProvidersFragment(
    private val plugin: Lateri3PlayPlugin,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")
    
    private lateinit var btnSave: ImageButton
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var adapter: ProviderAdapter
    private lateinit var container: LinearLayout
    private var providers: List<Provider> = emptyList()

    // FIX: Menggunakan string literal langsung untuk package name
    private fun <T : View> View.findView(name: String): T {
        val packageName = "com.AdiManuLateri3"
        val id = res.getIdentifier(name, "id", packageName)
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawable(name: String): Drawable? {
        val packageName = "com.AdiManuLateri3"
        val id = res.getIdentifier(name, "drawable", packageName)
        return if (id != 0) res.getDrawable(id, null) else null
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View.makeTvCompatible() {
        val packageName = "com.AdiManuLateri3"
        val id = res.getIdentifier("outline", "drawable", packageName)
        if (id != 0) {
            this.background = res.getDrawable(id, null)
        }
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val packageName = "com.AdiManuLateri3"
        val id = res.getIdentifier(name, "layout", packageName)
        return inflater.inflate(id, container, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return getLayout("fragment_providers", inflater, container)
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { dlg ->
            val bottomSheet = dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSave = view.findView("btn_save")
        btnSelectAll = view.findView("btn_select_all")
        btnDeselectAll = view.findView("btn_deselect_all")
        container = view.findView("list_container")
        val searchView = view.findView<SearchView>("search_provider")

        btnSave.setImageDrawable(getDrawable("save_icon"))
        btnSave.makeTvCompatible()
        
        providers = buildProviders().sortedBy { it.name.lowercase() }

        val savedDisabled = sharedPref.getStringSet(PREFS_DISABLED, emptySet()) ?: emptySet()

        adapter = ProviderAdapter(providers, savedDisabled) { disabled ->
            sharedPref.edit { putStringSet(PREFS_DISABLED, disabled) }
            updateUI()
        }

        // FIX: Hardcode package name
        val packageName = "com.AdiManuLateri3"
        val chkId = res.getIdentifier("chk_provider", "id", packageName)

        providers.forEach { provider ->
            val item = getLayout("item_provider_checkbox", layoutInflater, container)
            val chk = item.findViewById<CheckBox>(chkId)
            
            item.makeTvCompatible()
            chk.text = provider.name
            chk.isChecked = !adapter.isDisabled(provider.id)

            item.setOnClickListener { chk.toggle() }

            chk.setOnCheckedChangeListener { _, isChecked ->
                adapter.setDisabled(provider.id, !isChecked)
            }

            container.addView(item)
        }

        container.post {
            if (container.isNotEmpty()) {
                container.getChildAt(0).requestFocus()
            }
        }

        btnSelectAll.setOnClickListener { adapter.setAll(true); updateUI() }
        btnDeselectAll.setOnClickListener { adapter.setAll(false); updateUI() }
        btnSave.setOnClickListener { dismiss() }

        setupProfileFeatures(view)
        setupSearch(searchView, chkId)
    }

    private fun setupSearch(searchView: SearchView, chkId: Int) {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText.orEmpty().trim().lowercase()
                for (i in 0 until container.childCount) {
                    val item = container.getChildAt(i)
                    val chk = item.findViewById<CheckBox>(chkId)
                    val isVisible = chk.text.toString().lowercase().contains(query)
                    item.visibility = if (isVisible) View.VISIBLE else View.GONE
                }
                return true
            }
        })
    }

    private fun setupProfileFeatures(view: View) {
        val btnSaveProfile = view.findView<Button>("btn_save_profile")
        val btnLoadProfile = view.findView<Button>("btn_load_profile")
        val btnDeleteProfile = view.findView<Button>("btn_delete_profile")

        btnSaveProfile.setOnClickListener {
            val input = android.widget.EditText(requireContext())
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Simpan Profil")
                .setMessage("Beri nama profil ini:")
                .setView(input)
                .setPositiveButton("Simpan") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        saveProfile(name)
                        Toast.makeText(context, "Profil tersimpan", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        btnLoadProfile.setOnClickListener {
            val profiles = getAllProfiles().keys.toTypedArray()
            if (profiles.isEmpty()) {
                Toast.makeText(context, "Belum ada profil", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Pilih Profil")
                .setItems(profiles) { _, which ->
                    loadProfile(profiles[which])
                }
                .show()
        }

        btnDeleteProfile.setOnClickListener {
            val profiles = getAllProfiles().keys.toTypedArray()
            if (profiles.isEmpty()) return@setOnClickListener
            
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Hapus Profil")
                .setItems(profiles) { _, which ->
                    deleteProfile(profiles[which])
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun updateUI() {
        val packageName = "com.AdiManuLateri3"
        val chkId = res.getIdentifier("chk_provider", "id", packageName)
        for (i in 0 until container.childCount) {
            val chk = container.getChildAt(i).findViewById<CheckBox>(chkId)
            chk.isChecked = !adapter.isDisabled(providers[i].id)
        }
    }

    inner class ProviderAdapter(
        private val items: List<Provider>,
        initiallyDisabled: Set<String>,
        private val onChange: (Set<String>) -> Unit
    ) {
        private val disabled = initiallyDisabled.toMutableSet()

        fun isDisabled(id: String) = id in disabled

        fun setDisabled(id: String, value: Boolean) {
            if (value) disabled.add(id) else disabled.remove(id)
            onChange(disabled)
        }

        fun setAll(enable: Boolean) {
            disabled.clear()
            if (!enable) disabled.addAll(items.map { it.id })
            onChange(disabled)
        }
    }

    private fun saveProfile(name: String) {
        val disabled = sharedPref.getStringSet(PREFS_DISABLED, emptySet()) ?: emptySet()
        val allProfiles = getAllProfiles().toMutableMap()
        allProfiles[name] = disabled
        saveProfilesToPrefs(allProfiles)
    }

    private fun getAllProfiles(): Map<String, Set<String>> {
        val encoded = sharedPref.getString(PREFS_PROFILES, "") ?: return emptyMap()
        if (encoded.isEmpty()) return emptyMap()
        return encoded.split("|").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size < 2) return@mapNotNull null
            val name = parts[0]
            val ids = if (parts[1].isEmpty()) emptySet() else parts[1].split(",").toSet()
            name to ids
        }.toMap()
    }

    private fun loadProfile(name: String) {
        val profiles = getAllProfiles()
        val disabled = profiles[name] ?: return
        sharedPref.edit { putStringSet(PREFS_DISABLED, disabled) }
        
        adapter = ProviderAdapter(providers, disabled) { updated ->
            sharedPref.edit { putStringSet(PREFS_DISABLED, updated) }
            updateUI()
        }
        updateUI()
        Toast.makeText(context, "Profil dimuat", Toast.LENGTH_SHORT).show()
    }

    private fun deleteProfile(name: String) {
        val allProfiles = getAllProfiles().toMutableMap()
        if (allProfiles.remove(name) != null) {
            saveProfilesToPrefs(allProfiles)
            Toast.makeText(context, "Profil dihapus", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProfilesToPrefs(profiles: Map<String, Set<String>>) {
        val encoded = profiles.entries.joinToString("|") { (key, value) ->
            "$key:${value.joinToString(",")}"
        }
        sharedPref.edit { putString(PREFS_PROFILES, encoded) }
    }
}
