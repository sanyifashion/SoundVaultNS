package com.example.soundvault.ui.settings

import Views.VisualizerView
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.soundvault.MainActivity
import com.example.soundvault.R
import com.example.soundvault.data.AppDatabase
import com.example.soundvault.data.EqualizerPreset
import com.example.soundvault.data.SongPreset
import com.example.soundvault.databinding.FragmentSettingsBinding
import com.example.soundvault.services.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private var presetsList = listOf<EqualizerPreset>()
    private var isUpdatingSeekBars = false
    private var currentlySelectedPreset: EqualizerPreset? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        db = AppDatabase.getDatabase(requireContext())
        
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

        setupEqualizer()

        return binding.root
    }

    private fun setupEqualizer() {
        val mainActivity = activity as? MainActivity
        val musicService = mainActivity?.musicService
        
        val range = musicService?.getBandLevelRange() ?: intArrayOf(-1500, 1500)
        val minLevel = range[0]
        val maxLevel = range[1]
        
        binding.bassSeekBar.max = maxLevel - minLevel
        binding.midSeekBar.max = maxLevel - minLevel
        binding.trebleSeekBar.max = maxLevel - minLevel

        val seekBars = listOf(binding.bassSeekBar, binding.midSeekBar, binding.trebleSeekBar)
        seekBars.forEach { seekBar ->
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        applyCurrentSliders()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        lifecycleScope.launch {
            db.equalizerDao().getAllPresets().collectLatest { presets ->
                val b = _binding ?: return@collectLatest
                presetsList = listOf(MusicService.DEFAULT_PRESET) + presets
                val presetNames = presetsList.map { it.name }.toMutableList()
                presetNames.add(1, "Custom")
                
                val adapter = ArrayAdapter(requireContext(), R.layout.custom_spinner_item, presetNames)
                adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
                b.presetSpinner.adapter = adapter
                
                refreshEqState()
            }
        }

        binding.presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedName = binding.presetSpinner.selectedItem.toString()
                
                if (selectedName == "Custom") {
                    currentlySelectedPreset = null
                    updateButtonStates(isUpdate = false, showDelete = false)
                    return
                }
                
                val selectedPreset = presetsList.find { it.name == selectedName }
                currentlySelectedPreset = selectedPreset
                
                selectedPreset?.let {
                    updateSliders(it, minLevel)
                    (activity as? MainActivity)?.musicService?.applyPreset(it)
                    
                    val isCustomPreset = it.id != -1L
                    updateButtonStates(isUpdate = isCustomPreset, showDelete = isCustomPreset)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.savePresetBtn.setOnClickListener {
            if (currentlySelectedPreset != null && currentlySelectedPreset!!.id != -1L) {
                updateCurrentPreset()
            } else {
                showSavePresetDialog()
            }
        }

        binding.deletePresetBtn.setOnClickListener {
            currentlySelectedPreset?.let { showDeleteConfirmDialog(it) }
        }

        musicService?.currentMusic?.observe(viewLifecycleOwner) {
            refreshEqState()
        }
    }

    private fun updateButtonStates(isUpdate: Boolean, showDelete: Boolean) {
        binding.savePresetBtn.text = if (isUpdate) "Update Preset" else "Save Preset"
        binding.deletePresetBtn.visibility = if (showDelete) View.VISIBLE else View.GONE
    }

    private fun refreshEqState() {
        val b = _binding ?: return
        val musicService = (activity as? MainActivity)?.musicService
        val music = musicService?.currentMusic?.value
        val isMusicPlaying = music != null
        
        setEqControlsEnabled(isMusicPlaying)
        
        val range = musicService?.getBandLevelRange() ?: intArrayOf(-1500, 1500)
        val minLevel = range[0]

        if (isMusicPlaying && music != null) {
            lifecycleScope.launch {
                val currentPreset = db.equalizerDao().getPresetForSong(music.id) ?: MusicService.DEFAULT_PRESET
                
                val bNow = _binding ?: return@launch
                updateSliders(currentPreset, minLevel)
                
                if (bNow.presetSpinner.adapter != null) {
                    val presetNames = (0 until bNow.presetSpinner.count).map { bNow.presetSpinner.getItemAtPosition(it).toString() }
                    val index = presetNames.indexOf(currentPreset.name)
                    if (index >= 0 && bNow.presetSpinner.selectedItemPosition != index) {
                        bNow.presetSpinner.setSelection(index)
                    }
                }
            }
        } else {
            updateSliders(MusicService.DEFAULT_PRESET, minLevel)
            if (b.presetSpinner.adapter != null && b.presetSpinner.count > 0) {
                b.presetSpinner.setSelection(0) // Default
            }
        }
    }

    private fun setEqControlsEnabled(enabled: Boolean) {
        binding.eqControlsContainer.alpha = if (enabled) 1.0f else 0.5f
        binding.bassSeekBar.isEnabled = enabled
        binding.midSeekBar.isEnabled = enabled
        binding.trebleSeekBar.isEnabled = enabled
        binding.presetSpinner.isEnabled = enabled
        binding.savePresetBtn.isEnabled = enabled
        binding.eqHint.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun updateSliders(preset: EqualizerPreset, minLevel: Int) {
        isUpdatingSeekBars = true
        binding.bassSeekBar.progress = preset.bass - minLevel
        binding.midSeekBar.progress = preset.mid - minLevel
        binding.trebleSeekBar.progress = preset.treble - minLevel
        isUpdatingSeekBars = false
    }

    private fun applyCurrentSliders() {
        if (isUpdatingSeekBars) return
        
        val musicService = (activity as? MainActivity)?.musicService ?: return
        val range = musicService.getBandLevelRange()
        val minLevel = range[0]
        
        // Immediate audio effect update
        val tempPreset = EqualizerPreset(
            name = "Temp",
            bass = binding.bassSeekBar.progress + minLevel,
            mid = binding.midSeekBar.progress + minLevel,
            treble = binding.trebleSeekBar.progress + minLevel
        )
        musicService.applyPreset(tempPreset, saveForSong = false)
        
        // Handle UI transition to "Custom" or staying on a preset for update
        if (currentlySelectedPreset == null || currentlySelectedPreset?.id == -1L) {
            if (binding.presetSpinner.selectedItemPosition != 1) {
                binding.presetSpinner.setSelection(1) // Select "Custom"
            }
        }
    }

    private fun getColorFromAttr(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun showSavePresetDialog() {
        val container = FrameLayout(requireContext())
        val input = EditText(requireContext())
        val textColor = getColorFromAttr(R.attr.customTextColor)
        val primaryColor = getColorFromAttr(R.attr.customPrimaryColor)
        
        input.setTextColor(textColor)
        input.setHintTextColor(textColor and 0x80FFFFFF.toInt()) // 50% opacity
        input.backgroundTintList = ColorStateList.valueOf(primaryColor)
        
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics).toInt()
        lp.setMargins(margin, 0, margin, 0)
        input.layoutParams = lp
        input.hint = "Preset Name"
        
        container.addView(input)

        AlertDialog.Builder(requireContext())
            .setTitle("Save Preset")
            .setMessage("Enter preset name")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    savePreset(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePreset(name: String) {
        val musicService = (activity as? MainActivity)?.musicService ?: return
        val minLevel = musicService.getBandLevelRange()[0]
        
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) {
                db.equalizerDao().getPresetByName(name)
            }
            
            if (existing != null) {
                Toast.makeText(requireContext(), "A preset with this name already exists", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val preset = EqualizerPreset(
                name = name,
                bass = binding.bassSeekBar.progress + minLevel,
                mid = binding.midSeekBar.progress + minLevel,
                treble = binding.trebleSeekBar.progress + minLevel
            )

            val id = withContext(Dispatchers.IO) {
                db.equalizerDao().insertPreset(preset)
            }
            Toast.makeText(requireContext(), "Preset saved", Toast.LENGTH_SHORT).show()
            
            // Auto-link to song
            musicService.currentMusic.value?.let { music ->
                withContext(Dispatchers.IO) {
                    db.equalizerDao().insertSongPreset(SongPreset(music.id, id))
                }
            }
        }
    }

    private fun updateCurrentPreset() {
        val preset = currentlySelectedPreset ?: return
        val musicService = (activity as? MainActivity)?.musicService ?: return
        val minLevel = musicService.getBandLevelRange()[0]
        
        val updatedPreset = preset.copy(
            bass = binding.bassSeekBar.progress + minLevel,
            mid = binding.midSeekBar.progress + minLevel,
            treble = binding.trebleSeekBar.progress + minLevel
        )
        
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.equalizerDao().insertPreset(updatedPreset)
            }
            Toast.makeText(requireContext(), "Preset updated", Toast.LENGTH_SHORT).show()
            
            // Ensure the link is refreshed/active
            musicService.currentMusic.value?.let { music ->
                withContext(Dispatchers.IO) {
                    db.equalizerDao().insertSongPreset(SongPreset(music.id, updatedPreset.id))
                }
            }
        }
    }

    private fun showDeleteConfirmDialog(preset: EqualizerPreset) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Preset")
            .setMessage("Are you sure you want to delete '${preset.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                deletePreset(preset)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePreset(preset: EqualizerPreset) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.equalizerDao().deletePreset(preset)
            }
            Toast.makeText(requireContext(), "Preset deleted", Toast.LENGTH_SHORT).show()
            binding.presetSpinner.setSelection(0) // Back to default
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
