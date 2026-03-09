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
import com.example.soundvault.R
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
        
        // Setup Visualizer Style Spinner
        val visualizerThemes = VisualizerView.Theme.values().map { it.name }
        val vizAdapter = ArrayAdapter(requireContext(), R.layout.custom_spinner_item, visualizerThemes)
        vizAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        binding.themeSpinner.adapter = vizAdapter
        
        val currentVizTheme = sharedPrefs.getString("VisualizerTheme", VisualizerView.Theme.MOUNTAINS.name)
        binding.themeSpinner.setSelection(visualizerThemes.indexOf(currentVizTheme).coerceAtLeast(0))
        
        binding.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sharedPrefs.edit().putString("VisualizerTheme", visualizerThemes[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup App Theme Spinner
        val appThemes = listOf("Blue & White", "Red & Black", "Midnight Gold")
        val appThemeKeys = listOf("BLUE", "RED", "MIDNIGHT")
        val appAdapter = ArrayAdapter(requireContext(), R.layout.custom_spinner_item, appThemes)
        appAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        binding.appThemeSpinner.adapter = appAdapter

        val currentAppTheme = sharedPrefs.getString("AppTheme", "BLUE")
        binding.appThemeSpinner.setSelection(appThemeKeys.indexOf(currentAppTheme).coerceAtLeast(0))

        binding.appThemeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var firstSelection = true
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (firstSelection) {
                    firstSelection = false
                    return
                }
                val selectedTheme = appThemeKeys[position]
                if (selectedTheme != sharedPrefs.getString("AppTheme", "BLUE")) {
                    sharedPrefs.edit().putString("AppTheme", selectedTheme).apply()
                    activity?.recreate()
                }
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
