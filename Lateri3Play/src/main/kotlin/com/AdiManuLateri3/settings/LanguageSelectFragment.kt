package com.AdiManuLateri3

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class LanguageSelectFragment(
    private val plugin: Lateri3PlayPlugin,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res = plugin.resources ?: throw Exception("Unable to access plugin resources")

    private val languages = listOf(
        "Indonesia (Indonesian)" to "id-ID",
        "United States (English)" to "en-US",
        "United Kingdom (English)" to "en-GB",
        "Japan (Japanese)" to "ja-JP",
        "South Korea (Korean)" to "ko-KR",
        "China (Chinese Simplified)" to "zh-CN",
        "Taiwan (Chinese Traditional)" to "zh-TW",
        "France (French)" to "fr-FR",
        "Germany (German)" to "de-DE",
        "Italy (Italian)" to "it-IT",
        "Spain (Spanish)" to "es-ES",
        "Russia (Russian)" to "ru-RU",
        "India (Hindi)" to "hi-IN",
        "Thailand (Thai)" to "th-TH",
        "Vietnam (Vietnamese)" to "vi-VN",
        "Brazil (Portuguese)" to "pt-BR",
        "Philippines (Filipino)" to "tl-PH",
        "Malaysia (Malay)" to "ms-MY",
        "Turkey (Turkish)" to "tr-TR",
        "Saudi Arabia (Arabic)" to "ar-SA"
    ).sortedBy { it.first }

    private lateinit var adapter: LanguageAdapter

    // FIX: Menggunakan string literal langsung untuk package name
    private fun getResId(name: String, type: String): Int {
        val packageName = "com.AdiManuLateri3"
        return res.getIdentifier(name, type, packageName)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View.makeTvCompatible() {
        val outlineId = getResId("outline", "drawable")
        if (outlineId != 0) {
            this.background = res.getDrawable(outlineId, null)
        }
    }

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = getResId(name, "layout")
        return inflater.inflate(id, container, false)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = getResId(name, "id")
        if (id == 0) throw Exception("View ID $name not found.")
        return this.findViewById(id)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = getLayout("fragment_language_select", inflater, container)

        val recycler: RecyclerView = root.findView("languageRecycler")
        val search: EditText = root.findView("searchLanguage")
        
        recycler.makeTvCompatible()
        search.makeTvCompatible()

        recycler.layoutManager = LinearLayoutManager(requireContext())

        val savedCode = sharedPref.getString("tmdb_language_code", "en-US") ?: "en-US"

        adapter = LanguageAdapter(languages, savedCode) { code ->
            sharedPref.edit { putString("tmdb_language_code", code) }
            Toast.makeText(requireContext(), "Bahasa diubah ke $code.", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        recycler.adapter = adapter

        search.addTextChangedListener { text ->
            adapter.filter(text.toString())
        }

        return root
    }

    inner class LanguageAdapter(
        private val originalList: List<Pair<String, String>>,
        private val selectedCode: String,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.VH>() {

        private var filteredList = originalList.toMutableList()

        inner class VH(val v: View) : RecyclerView.ViewHolder(v) {
            val radio: RadioButton = v.findViewById(getResId("radio_language", "id"))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = getLayout("item_language", LayoutInflater.from(parent.context), parent)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (name, code) = filteredList[position]

            holder.radio.text = name
            holder.radio.isChecked = code == selectedCode

            holder.itemView.setOnClickListener { onClick(code) }
            holder.radio.setOnClickListener { onClick(code) }
        }

        override fun getItemCount() = filteredList.size

        @SuppressLint("NotifyDataSetChanged")
        fun filter(query: String) {
            filteredList = if (query.isBlank()) {
                originalList.toMutableList()
            } else {
                originalList.filter { it.first.contains(query, ignoreCase = true) }.toMutableList()
            }
            notifyDataSetChanged()
        }
    }
}
