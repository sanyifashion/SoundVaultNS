package com.example.soundvault.ui.settings

import Views.VisualizerView
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.example.soundvault.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        
        val sharedPrefs = requireContext().getSharedPreferences("SoundVaultPrefs", Context.MODE_PRIVATE)
        
        // Setup Spinner
        val themes = VisualizerView.Theme.values().map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.themeSpinner.adapter = adapter
        
        val currentTheme = sharedPrefs.getString("VisualizerTheme", VisualizerView.Theme.MOUNTAINS.name)
        val initialPosition = themes.indexOf(currentTheme)
        if (initialPosition >= 0) {
            binding.themeSpinner.setSelection(initialPosition)
        }
        
        binding.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedTheme = themes[position]
                sharedPrefs.edit().putString("VisualizerTheme", selectedTheme).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Setup Tap Toggle
        val allowTap = sharedPrefs.getBoolean("AllowVisualizerTap", true)
        binding.tapToggle.isChecked = allowTap
        binding.tapToggle.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("AllowVisualizerTap", isChecked).apply()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
