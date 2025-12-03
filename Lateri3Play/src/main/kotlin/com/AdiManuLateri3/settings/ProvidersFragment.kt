package com.AdiManuLateri3.settings

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
import com.AdiManuLateri3.Lateri3PlayPlugin
import com.AdiManuLateri3.Provider
import com.AdiManuLateri3.buildProviders

private const val PREFS_PROFILES = "provider_profiles"
private const val PREFS_DISABLED = "disabled_providers"

class ProvidersFragment(
    private val plugin: Lateri3PlayPlugin,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")
    private val packageName = "com.AdiManuLateri3"
    
    private lateinit var btnSave: ImageButton
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var adapter: ProviderAdapter
    private lateinit var container: LinearLayout
    private var providers: List<Provider> = emptyList()

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", packageName)
        if (id == 0) throw Exception("View ID $name not found in package $packageName")
        return this.findViewById(id)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) throw Exception("Drawable $name not found")
        return res.getDrawable(id, null) ?: throw Exception("Drawable cannot be loaded")
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View.makeTvCompatible() {
        try {
            val outlineId = res.getIdentifier("outline", "drawable", packageName)
            if (outlineId != 0) {
                this.background = res.getDrawable(outlineId, null)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", packageName)
        if (id == 0) throw Exception("Layout $name not found")
        val parser = res.getLayout(id)
        return inflater.inflate(parser, container, false)
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
                behavior.isDraggable = true
                behavior.skipCollapsed = true
                sheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSave = view.findView("btn_save")
        btnSave.setImageDrawable(getDrawable("save_icon"))
        btnSave.makeTvCompatible()

        btnSelectAll = view.findView("btn_select_all")
        btnDeselectAll = view.findView("btn_deselect_all")
        
        container = view.findView("list_container")
        container.makeTvCompatible()
        
        providers = buildProviders().sortedBy { it.name.lowercase() }

        val savedDisabled = sharedPref.getStringSet(PREFS_DISABLED, emptySet()) ?: emptySet()

        adapter = ProviderAdapter(providers, savedDisabled) { disabled ->
            sharedPref.edit { putStringSet(PREFS_DISABLED, disabled) }
            updateUI()
        }

        val chkId = res.getIdentifier("chk_provider", "id", packageName)

        providers.forEach { provider ->
            val item = getLayout("item_provider_checkbox", layoutInflater, container)
            val chk = item.findViewById<CheckBox>(chkId)
            item.makeTvCompatible()
            chk.makeTvCompatible()
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
                val firstItem = container.getChildAt(0)
                firstItem.isFocusable = true
                firstItem.requestFocusFromTouch()
                firstItem.nextFocusUpId = btnSave.id
            }
        }

        btnSelectAll.setOnClickListener { adapter.setAll(true) }
        btnDeselectAll.setOnClickListener { adapter.setAll(false) }
        btnSave.setOnClickListener { dismissFragment() }

        // --- Profile Handling ---
        val btnSaveProfile = view.findView<Button>("btn_save_profile")
        val btnLoadProfile = view.findView<Button>("btn_load_profile")
        val btnDeleteProfile = view.findView<Button>("btn_delete_profile")

        btnSaveProfile.setOnClickListener {
            val input = android.widget.EditText(requireContext())
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Save Profile")
                .setMessage("Enter profile name:")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        saveProfile(name)
                        showMessage("Profile saved.")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnLoadProfile.setOnClickListener {
            val profiles = getAllProfiles().keys.toTypedArray()
            if (profiles.isEmpty()) {
                showMessage("No profiles found.")
                return@setOnClickListener
            }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Select Profile")
                .setItems(profiles) { _, which ->
                    loadProfile(profiles[which])
                }
                .show()
        }

        btnDeleteProfile.setOnClickListener {
            val profiles = getAllProfiles().keys.toTypedArray()
            if (profiles.isEmpty()) return@setOnClickListener
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Profile")
                .setItems(profiles) { _, which ->
                    deleteProfile(profiles[which])
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        val searchView = view.findView<SearchView>("search_provider")
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

    private fun updateUI() {
        val chkId = res.getIdentifier("chk_provider", "id", packageName)
        for (i in 0 until container.childCount) {
            val chk = container.getChildAt(i).findViewById<CheckBox>(chkId)
            chk.isChecked = !adapter.isDisabled(providers[i].id)
        }
    }

    private fun dismissFragment() {
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
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

        fun setAll(value: Boolean) {
            disabled.clear()
            if (!value) disabled.addAll(items.map { it.id })
            onChange(disabled)
        }
    }

    private fun saveProfile(name: String) {
        val disabled = sharedPref.getStringSet(PREFS_DISABLED, emptySet()) ?: emptySet()
        val allProfiles = getAllProfiles().toMutableMap()
        allProfiles[name] = disabled
        val encoded = allProfiles.entries.joinToString("|") { "${it.key}:${it.value.joinToString(",")}" }
        sharedPref.edit { putString(PREFS_PROFILES, encoded) }
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
        val disabled = getAllProfiles()[name] ?: return
        sharedPref.edit { putStringSet(PREFS_DISABLED, disabled) }
        adapter = ProviderAdapter(providers, disabled) { updated ->
            sharedPref.edit { putStringSet(PREFS_DISABLED, updated) }
            updateUI()
        }
        updateUI()
    }

    private fun deleteProfile(name: String) {
        val allProfiles = getAllProfiles().toMutableMap()
        if (allProfiles.remove(name) != null) {
            val encoded = allProfiles.entries.joinToString("|") { "${it.key}:${it.value.joinToString(",")}" }
            sharedPref.edit { putString(PREFS_PROFILES, encoded) }
            showMessage("Deleted.")
        }
    }

    private fun showMessage(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
