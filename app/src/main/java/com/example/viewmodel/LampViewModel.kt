package com.example.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbientSoundSynthesizer
import com.example.audio.AmbientSoundType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ColorPreset(
    val name: String,
    val color: Color,
    val tempK: Int,
    val description: String
)

data class ReadingPreset(
    val name: String,
    val brightness: Float,
    val color: Color,
    val tempK: Int,
    val isRelaxing: Boolean
)

class LampViewModel : ViewModel() {

    private val soundSynthesizer = AmbientSoundSynthesizer()

    // 1. Core Lamp States
    private val _isOn = MutableStateFlow(true)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    private val _brightness = MutableStateFlow(0.70f) // 70% default
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _roomDarkness = MutableStateFlow(0.75f) // 75% default
    val roomDarkness: StateFlow<Float> = _roomDarkness.asStateFlow()

    private val _isAmbientDark = MutableStateFlow(true) // default to dark immersive
    val isAmbientDark: StateFlow<Boolean> = _isAmbientDark.asStateFlow()

    fun setAmbientDark(enabled: Boolean) {
        _isAmbientDark.value = enabled
        _roomDarkness.value = if (enabled) 0.95f else 0.0f
    }

    fun toggleAmbientDark() {
        setAmbientDark(!_isAmbientDark.value)
    }

    private val _isFocusMode = MutableStateFlow(false)
    val isFocusMode: StateFlow<Boolean> = _isFocusMode.asStateFlow()

    private val _isAdjustingBrightness = MutableStateFlow(false)
    val isAdjustingBrightness: StateFlow<Boolean> = _isAdjustingBrightness.asStateFlow()

    fun setAdjustingBrightness(value: Boolean) {
        _isAdjustingBrightness.value = value
    }

    private val _hasWriteSettingsPermission = MutableStateFlow(false)
    val hasWriteSettingsPermission: StateFlow<Boolean> = _hasWriteSettingsPermission.asStateFlow()

    private val _systemBrightnessSync = MutableStateFlow(true) // Enabled by default
    val systemBrightnessSync: StateFlow<Boolean> = _systemBrightnessSync.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    fun checkWriteSettingsPermission(context: android.content.Context) {
        _hasWriteSettingsPermission.value = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.provider.Settings.System.canWrite(context)
        } else {
            true
        }
    }

    fun setSystemBrightnessSync(enabled: Boolean) {
        _systemBrightnessSync.value = enabled
    }

    fun setShowPermissionDialog(show: Boolean) {
        _showPermissionDialog.value = show
    }

    private val _lampScale = MutableStateFlow(1.0f) // Pinch to resize factor (0.8f to 1.5f)
    val lampScale: StateFlow<Float> = _lampScale.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color(0xFFFFE8B0)) // Default Soft White (3000K)
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    private val _colorTempK = MutableStateFlow(3000)
    val colorTempK: StateFlow<Int> = _colorTempK.asStateFlow()

    private val _activePresetName = MutableStateFlow<String?>("Soft White")
    val activePresetName: StateFlow<String?> = _activePresetName.asStateFlow()

    // 2. Timer States
    private val _timerSecondsLeft = MutableStateFlow(-1L)
    val timerSecondsLeft: StateFlow<Long> = _timerSecondsLeft.asStateFlow()

    private val _timerDurationSeconds = MutableStateFlow(-1L)
    val timerDurationSeconds: StateFlow<Long> = _timerDurationSeconds.asStateFlow()

    private var timerJob: Job? = null

    // 2.5 Bulb Position State for Realistic Radial Light Falloff
    private val _bulbPosition = MutableStateFlow<androidx.compose.ui.geometry.Offset?>(null)
    val bulbPosition: StateFlow<androidx.compose.ui.geometry.Offset?> = _bulbPosition.asStateFlow()

    fun updateBulbPosition(position: androidx.compose.ui.geometry.Offset) {
        _bulbPosition.value = position
    }

    // 3. Audio States
    private val _ambientSound = MutableStateFlow(AmbientSoundType.NONE)
    val ambientSound: StateFlow<AmbientSoundType> = _ambientSound.asStateFlow()

    private val _ambientVolume = MutableStateFlow(0.5f) // 50% default
    val ambientVolume: StateFlow<Float> = _ambientVolume.asStateFlow()

    private val _isPlayingSound = MutableStateFlow(false)
    val isPlayingSound: StateFlow<Boolean> = _isPlayingSound.asStateFlow()

    // 4. Color Picker Customization States
    private val _isAdvancedColorPickerOpen = MutableStateFlow(false)
    val isAdvancedColorPickerOpen: StateFlow<Boolean> = _isAdvancedColorPickerOpen.asStateFlow()

    private val _recentColors = MutableStateFlow<List<Color>>(
        listOf(
            Color(0xFF111111), // Primary Black
            Color(0xFFFFE8B0), // Soft White
            Color(0xFFFF7E40), // Sunset Orange
            Color(0xFFFFC984)  // Warm Relax
        )
    )
    val recentColors: StateFlow<List<Color>> = _recentColors.asStateFlow()

    private val _favoriteColors = MutableStateFlow<List<Color>>(
        listOf(
            Color(0xFFFFD1A9),
            Color(0xFFE8F0FE),
            Color(0xFFFF8A8A),
            Color(0xFFB9F6CA)
        )
    )
    val favoriteColors: StateFlow<List<Color>> = _favoriteColors.asStateFlow()

    // Color Presets defined in Kelvin or Vibe
    val colorPresets = listOf(
        ColorPreset("Warm White", Color(0xFFFFE1A8), 2700, "2700K Cozy Room Glow"),
        ColorPreset("Soft White", Color(0xFFFFE8B0), 3000, "3000K Standard Lamp"),
        ColorPreset("Neutral White", Color(0xFFFFF1D6), 4000, "4000K Natural Light"),
        ColorPreset("Cool White", Color(0xFFF0F5FF), 5000, "5000K Crisp Focus"),
        ColorPreset("Daylight", Color(0xFFE3EDFF), 6500, "6500K Reading Daylight"),
        ColorPreset("Sunset Glow", Color(0xFFFF7E40), 1800, "1800K Relaxing Sunset"),
        ColorPreset("Candlelight", Color(0xFFFF9D3B), 1500, "1500K Soft Amber Flicker"),
        ColorPreset("Moonlight", Color(0xFFD4E4FC), 5500, "5500K Cool Silver Night")
    )

    // Reading / Task Presets
    val readingPresets = listOf(
        ReadingPreset("Reading", 0.85f, Color(0xFFFFE2B0), 2800, false),
        ReadingPreset("Study", 0.95f, Color(0xFFFFF3D6), 4200, false),
        ReadingPreset("Focus", 1.00f, Color(0xFFF2F6FF), 5200, false),
        ReadingPreset("Relax", 0.45f, Color(0xFFFFC57A), 2200, true),
        ReadingPreset("Night", 0.15f, Color(0xFFFF9E4B), 1600, true),
        ReadingPreset("Sleep", 0.05f, Color(0xFFFF851B), 1200, true),
        ReadingPreset("Meditation", 0.30f, Color(0xFFD8E4FF), 5000, true)
    )

    // 5. Actions / Functions

    fun togglePower() {
        _isOn.value = !_isOn.value
        handlePowerSoundState()
    }

    fun setPower(on: Boolean) {
        if (_isOn.value != on) {
            _isOn.value = on
            handlePowerSoundState()
        }
    }

    private fun handlePowerSoundState() {
        if (!_isOn.value) {
            // Pause sounds when lamp turns off
            if (_isPlayingSound.value) {
                soundSynthesizer.stop()
            }
        } else {
            // Resume sound if active
            if (_isPlayingSound.value && _ambientSound.value != AmbientSoundType.NONE) {
                soundSynthesizer.start(_ambientSound.value, _ambientVolume.value)
            }
        }
    }

    fun setBrightness(value: Float) {
        _brightness.value = value.coerceIn(0f, 1f)
    }

    fun setRoomDarkness(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _roomDarkness.value = clamped
        _isAmbientDark.value = clamped >= 0.5f
    }

    fun setFocusMode(enabled: Boolean) {
        _isFocusMode.value = enabled
    }

    fun toggleFocusMode() {
        _isFocusMode.value = !_isFocusMode.value
    }

    fun setLampScale(scale: Float) {
        _lampScale.value = scale.coerceIn(0.7f, 1.6f)
    }

    fun selectColorPreset(preset: ColorPreset) {
        _selectedColor.value = preset.color
        _colorTempK.value = preset.tempK
        _activePresetName.value = preset.name
    }

    fun selectReadingPreset(preset: ReadingPreset) {
        _brightness.value = preset.brightness
        _selectedColor.value = preset.color
        _colorTempK.value = preset.tempK
        _activePresetName.value = preset.name
        _isOn.value = true
        handlePowerSoundState()
    }

    fun selectCustomColor(color: Color) {
        _selectedColor.value = color
        _activePresetName.value = "Custom"
        
        // Add to recents if not exists
        val currentRecents = _recentColors.value.toMutableList()
        if (!currentRecents.contains(color)) {
            currentRecents.add(0, color)
            if (currentRecents.size > 8) {
                currentRecents.removeAt(currentRecents.lastIndex)
            }
            _recentColors.value = currentRecents
        }
    }

    fun toggleFavoriteColor(color: Color) {
        val currentFavs = _favoriteColors.value.toMutableList()
        if (currentFavs.contains(color)) {
            currentFavs.remove(color)
        } else {
            currentFavs.add(0, color)
        }
        _favoriteColors.value = currentFavs
    }

    fun setAdvancedColorPickerOpen(open: Boolean) {
        _isAdvancedColorPickerOpen.value = open
    }

    fun selectColorTempK(tempK: Int) {
        _colorTempK.value = tempK
        _activePresetName.value = "Custom Temp"
        
        // Accurate Blackbody Radiation approximation (Kelvin to RGB)
        val r: Float
        val g: Float
        val b: Float
        val t = tempK / 100.0
        if (t <= 66) {
            r = 255f
            g = if (t <= 0) 0f else (99.4708025861 * Math.log(t) - 161.1195681661).toFloat().coerceIn(0f, 255f)
            b = if (t <= 19) 0f else (138.5177312231 * Math.log(t - 10) - 305.0447927307).toFloat().coerceIn(0f, 255f)
        } else {
            r = if (t <= 60) 255f else (329.698727446 * Math.pow(t - 60, -0.1332047592)).toFloat().coerceIn(0f, 255f)
            g = if (t <= 60) 255f else (288.1221695283 * Math.pow(t - 60, -0.0755148492)).toFloat().coerceIn(0f, 255f)
            b = 255f
        }
        _selectedColor.value = Color(r / 255f, g / 255f, b / 255f)
    }

    // 6. Timer Implementation
    fun startTimer(durationMinutes: Int) {
        timerJob?.cancel()
        if (durationMinutes <= 0) {
            _timerSecondsLeft.value = -1L
            _timerDurationSeconds.value = -1L
            return
        }

        val totalSeconds = durationMinutes * 60L
        _timerDurationSeconds.value = totalSeconds
        _timerSecondsLeft.value = totalSeconds

        timerJob = viewModelScope.launch {
            while (_timerSecondsLeft.value > 0) {
                delay(1000)
                _timerSecondsLeft.value--
            }
            // Timer expired! Execute beautiful gradual fade out
            executeTimerFadeOut()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        _timerSecondsLeft.value = -1L
        _timerDurationSeconds.value = -1L
    }

    fun extendTimer(minutes: Int) {
        val currentLeft = _timerSecondsLeft.value
        if (currentLeft > 0) {
            val newSeconds = currentLeft + minutes * 60L
            _timerSecondsLeft.value = newSeconds
            _timerDurationSeconds.value = _timerDurationSeconds.value + minutes * 60L
        } else {
            startTimer(minutes)
        }
    }

    private fun executeTimerFadeOut() {
        viewModelScope.launch {
            val initialBrightness = _brightness.value
            val initialVolume = _ambientVolume.value
            val fadeSteps = 30 // Smooth transition over 3 seconds (100ms per step)
            
            for (step in 1..fadeSteps) {
                val ratio = (fadeSteps - step).toFloat() / fadeSteps
                _brightness.value = initialBrightness * ratio
                if (_isPlayingSound.value) {
                    val tempVol = initialVolume * ratio
                    soundSynthesizer.setVolume(tempVol)
                }
                delay(100)
            }

            // Final shut-off
            _isOn.value = false
            _brightness.value = initialBrightness // restore default value for next turn on
            soundSynthesizer.stop()
            _isPlayingSound.value = false
            _timerSecondsLeft.value = -1L
            _timerDurationSeconds.value = -1L
        }
    }

    private var ambientFadeJob: Job? = null

    // 7. Ambient Audio Actions
    fun selectAmbientSound(soundType: AmbientSoundType) {
        if (_ambientSound.value == soundType && _isPlayingSound.value) {
            togglePlaySound()
            return
        }

        ambientFadeJob?.cancel()
        ambientFadeJob = viewModelScope.launch {
            val targetVol = _ambientVolume.value
            if (_isPlayingSound.value && _ambientSound.value != AmbientSoundType.NONE) {
                // Fade out previous sound over ~500ms
                val steps = 10
                for (i in 1..steps) {
                    val tempVol = targetVol * (steps - i) / steps.toFloat()
                    soundSynthesizer.setVolume(tempVol)
                    delay(50)
                }
            }

            _ambientSound.value = soundType
            if (soundType == AmbientSoundType.NONE) {
                _isPlayingSound.value = false
                soundSynthesizer.stop()
            } else {
                _isPlayingSound.value = true
                soundSynthesizer.start(soundType, 0f)
                // Fade in new sound over ~500ms
                val steps = 10
                for (i in 1..steps) {
                    val tempVol = targetVol * i / steps.toFloat()
                    soundSynthesizer.setVolume(tempVol)
                    delay(50)
                }
            }
        }
    }

    fun togglePlaySound() {
        if (_ambientSound.value == AmbientSoundType.NONE) return
        
        ambientFadeJob?.cancel()
        ambientFadeJob = viewModelScope.launch {
            val targetVol = _ambientVolume.value
            if (_isPlayingSound.value) {
                // Fade out over ~500ms
                val steps = 10
                for (i in 1..steps) {
                    val tempVol = targetVol * (steps - i) / steps.toFloat()
                    soundSynthesizer.setVolume(tempVol)
                    delay(50)
                }
                soundSynthesizer.stop()
                _isPlayingSound.value = false
            } else {
                _isPlayingSound.value = true
                soundSynthesizer.start(_ambientSound.value, 0f)
                // Fade in over ~500ms
                val steps = 10
                for (i in 1..steps) {
                    val tempVol = targetVol * i / steps.toFloat()
                    soundSynthesizer.setVolume(tempVol)
                    delay(50)
                }
            }
        }
    }

    fun setAmbientVolume(volume: Float) {
        _ambientVolume.value = volume
        if (_isPlayingSound.value) {
            soundSynthesizer.setVolume(volume)
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundSynthesizer.stop()
    }
}
