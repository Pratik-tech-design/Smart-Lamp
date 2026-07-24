package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class InteractionFeedbackManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var tickSoundId: Int = -1
    private var strongTickSoundId: Int = -1
    private var toggleSoundId: Int = -1
    private var brightnessTickSoundId: Int = -1
    private var glassCrackSoundId: Int = -1
    private var glassShatterSoundId: Int = -1
    private var cableRetractSoundId: Int = -1
    private var lampLowerSoundId: Int = -1
    private var powerOnSoundId: Int = -1

    private var lastTickTime: Long = 0L
    private var lastBrightnessStreamId: Int = -1
    private var lastGlassCrackStreamId: Int = -1
    private var lastGlassShatterStreamId: Int = -1

    private val attrContext: Context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.createAttributionContext("default")
    } else {
        context
    }

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = attrContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                attrContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    enum class HapticStyle {
        TICK,
        CLICK,
        HEAVY_CLICK
    }

    init {
        try {
            // Play through the Device Media stream (USAGE_MEDIA) for optimal audio quality and volume matching
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(attrs)
                .build()

            // Generate and load tick sound
            val tickFile = File(context.cacheDir, "tick_crisp.wav")
            writeWavFile(tickFile, generateTickPcm(isStrong = false))
            tickSoundId = soundPool?.load(tickFile.absolutePath, 1) ?: -1

            // Generate and load strong tick sound
            val strongTickFile = File(context.cacheDir, "tick_strong.wav")
            writeWavFile(strongTickFile, generateTickPcm(isStrong = true))
            strongTickSoundId = soundPool?.load(strongTickFile.absolutePath, 1) ?: -1

            // Generate and load premium lamp toggle sound
            val toggleFile = File(context.cacheDir, "lamp_toggle.wav")
            writeWavFile(toggleFile, generateTogglePcm())
            toggleSoundId = soundPool?.load(toggleFile.absolutePath, 1) ?: -1

            // Generate and load brightness tick sound
            val brightnessTickFile = File(context.cacheDir, "brightness_tick.wav")
            writeWavFile(brightnessTickFile, generateBrightnessTickPcm())
            brightnessTickSoundId = soundPool?.load(brightnessTickFile.absolutePath, 1) ?: -1

            // Generate and load glass crack sound
            val glassCrackFile = File(context.cacheDir, "glass_crack.wav")
            writeWavFile(glassCrackFile, generateGlassCrackPcm())
            glassCrackSoundId = soundPool?.load(glassCrackFile.absolutePath, 1) ?: -1

            // Generate and load glass shatter sound
            val glassShatterFile = File(context.cacheDir, "glass_shatter.wav")
            writeWavFile(glassShatterFile, generateGlassShatterPcm())
            glassShatterSoundId = soundPool?.load(glassShatterFile.absolutePath, 1) ?: -1

            // Generate and load cable retract sound
            val cableRetractFile = File(context.cacheDir, "cable_retract.wav")
            writeWavFile(cableRetractFile, generateCableRetractPcm())
            cableRetractSoundId = soundPool?.load(cableRetractFile.absolutePath, 1) ?: -1

            // Generate and load lamp lower sound
            val lampLowerFile = File(context.cacheDir, "lamp_lower.wav")
            writeWavFile(lampLowerFile, generateLampLowerPcm())
            lampLowerSoundId = soundPool?.load(lampLowerFile.absolutePath, 1) ?: -1

            // Generate and load power-on sound
            val powerOnFile = File(context.cacheDir, "power_on.wav")
            writeWavFile(powerOnFile, generatePowerOnPcm())
            powerOnSoundId = soundPool?.load(powerOnFile.absolutePath, 1) ?: -1
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isSoundEffectsEnabled(): Boolean {
        return try {
            android.provider.Settings.System.getInt(
                context.contentResolver,
                android.provider.Settings.System.SOUND_EFFECTS_ENABLED,
                1
            ) != 0
        } catch (e: Exception) {
            true
        }
    }

    private fun isHapticFeedbackEnabled(): Boolean {
        return try {
            android.provider.Settings.System.getInt(
                context.contentResolver,
                android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            ) != 0
        } catch (e: Exception) {
            true
        }
    }

    private fun triggerHaptic(view: View, style: HapticStyle) {
        if (!isHapticFeedbackEnabled()) return
        val v = vibrator
        if (v != null && v.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use manufacturer-calibrated native hardware effects (extremely subtle, 15-20% intensity, no buzzing)
                    val effectId = when (style) {
                        HapticStyle.TICK -> VibrationEffect.EFFECT_TICK
                        HapticStyle.CLICK -> VibrationEffect.EFFECT_CLICK
                        HapticStyle.HEAVY_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    }
                    v.vibrate(VibrationEffect.createPredefined(effectId))
                    return
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Pre-Q fallback: extremely short, low-amplitude one-shots
                    val effect = when (style) {
                        HapticStyle.TICK -> VibrationEffect.createOneShot(4, 12)
                        HapticStyle.CLICK -> VibrationEffect.createOneShot(6, 20)
                        HapticStyle.HEAVY_CLICK -> VibrationEffect.createOneShot(12, 45)
                    }
                    v.vibrate(effect)
                    return
                }
            } catch (e: Exception) {
                // Fallback to view performance haptic
            }
        }
        
        // Fallback to standard lightweight system view constants
        val constant = when (style) {
            HapticStyle.TICK -> HapticFeedbackConstants.CLOCK_TICK
            HapticStyle.CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticStyle.HEAVY_CLICK -> HapticFeedbackConstants.CONFIRM
        }
        view.performHapticFeedback(constant)
    }

    fun playInteractionClick(view: View) {
        if (isSoundEffectsEnabled()) {
            val soundId = tickSoundId
            if (soundId != -1) {
                // Play with highly audible, mastered presence volume (0.85f instead of 0.20f)
                soundPool?.play(soundId, 0.85f, 0.85f, 1, 0, 1.0f)
            }
        }
        triggerHaptic(view, HapticStyle.CLICK)
    }

    fun playContinuousTick(view: View, isEdge: Boolean) {
        if (isSoundEffectsEnabled()) {
            val soundId = if (isEdge) strongTickSoundId else tickSoundId
            if (soundId != -1) {
                // Play with clear loudness (0.90f / 0.70f instead of 0.30f / 0.18f)
                val volume = if (isEdge) 0.90f else 0.70f
                soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
            }
        }
        if (isEdge) {
            triggerHaptic(view, HapticStyle.HEAVY_CLICK)
        } else {
            triggerHaptic(view, HapticStyle.TICK)
        }
    }

    fun playToggleSound(view: View) {
        if (isSoundEffectsEnabled()) {
            val soundId = toggleSoundId
            if (soundId != -1) {
                // Play with loud, crisp presence (0.95f instead of 0.40f)
                soundPool?.play(soundId, 0.95f, 0.95f, 1, 0, 1.0f)
            }
        }
        triggerHaptic(view, HapticStyle.HEAVY_CLICK)
    }

    fun playBrightnessTick(view: View, brightness: Float) {
        if (!isSoundEffectsEnabled() && !isHapticFeedbackEnabled()) return

        val now = System.currentTimeMillis()
        // Rate-limit very rapid scrolls to 35ms spacing so they don't sound like noisy buzz
        if (now - lastTickTime < 35L) {
            return
        }
        lastTickTime = now

        val isZero = brightness <= 0.015f
        val isFull = brightness >= 0.985f
        val isHalf = Math.abs(brightness - 0.5f) <= 0.02f

        // Stop last stream to ensure absolutely NO overlapping audio
        val prevStreamId = lastBrightnessStreamId
        if (prevStreamId != -1) {
            soundPool?.stop(prevStreamId)
            lastBrightnessStreamId = -1
        }

        if (isSoundEffectsEnabled()) {
            val soundId = if (isZero || isFull) {
                strongTickSoundId
            } else {
                brightnessTickSoundId
            }
            if (soundId != -1) {
                // Mastered loudness (0.90f / 0.75f / 0.60f instead of 0.38f / 0.26f / 0.16f)
                val volume = if (isZero || isFull) 0.90f else if (isHalf) 0.75f else 0.60f
                lastBrightnessStreamId = soundPool?.play(soundId, volume, volume, 1, 0, 1.0f) ?: -1
            }
        }

        if (isHapticFeedbackEnabled()) {
            if (isZero || isFull) {
                triggerHaptic(view, HapticStyle.HEAVY_CLICK)
            } else if (isHalf) {
                triggerHaptic(view, HapticStyle.CLICK)
            } else {
                triggerHaptic(view, HapticStyle.TICK)
            }
        }
    }

    fun stopBrightnessTicks() {
        val streamId = lastBrightnessStreamId
        if (streamId != -1) {
            soundPool?.stop(streamId)
            lastBrightnessStreamId = -1
        }
    }

    fun playGlassCrack(view: View, level: Int = 1) {
        if (isSoundEffectsEnabled()) {
            if (glassCrackSoundId != -1) {
                if (lastGlassCrackStreamId != -1) {
                    soundPool?.stop(lastGlassCrackStreamId)
                    lastGlassCrackStreamId = -1
                }
                val volume = if (level == 1) 0.65f else 0.95f
                val rate = if (level == 1) 1.15f else 1.0f
                lastGlassCrackStreamId = soundPool?.play(glassCrackSoundId, volume, volume, 1, 0, rate) ?: -1
            }
        }
        if (level == 1) {
            triggerHaptic(view, HapticStyle.CLICK)
        } else {
            triggerHaptic(view, HapticStyle.HEAVY_CLICK)
        }
    }

    fun playGlassShatter(view: View) {
        if (isSoundEffectsEnabled()) {
            if (glassShatterSoundId != -1) {
                if (lastGlassShatterStreamId != -1) {
                    soundPool?.stop(lastGlassShatterStreamId)
                    lastGlassShatterStreamId = -1
                }
                // Play sound once (loop = 0), rate = 1.0f (no pitch shift / speed change), clear volume
                lastGlassShatterStreamId = soundPool?.play(glassShatterSoundId, 0.95f, 0.95f, 1, 0, 1.0f) ?: -1
            }
        }
        // Subtle secondary vibration during main shatter for added realism
        triggerHaptic(view, HapticStyle.TICK)
    }

    fun playCableRetract(view: View) {
        if (isSoundEffectsEnabled()) {
            if (cableRetractSoundId != -1) {
                soundPool?.play(cableRetractSoundId, 0.70f, 0.70f, 1, 0, 1.0f)
            }
        }
    }

    fun playLampLower(view: View) {
        if (isSoundEffectsEnabled()) {
            if (lampLowerSoundId != -1) {
                soundPool?.play(lampLowerSoundId, 0.75f, 0.75f, 1, 0, 1.0f)
            }
        }
    }

    fun playPowerOn(view: View) {
        if (isSoundEffectsEnabled()) {
            if (powerOnSoundId != -1) {
                soundPool?.play(powerOnSoundId, 0.90f, 0.90f, 1, 0, 1.0f)
            }
        }
        triggerHaptic(view, HapticStyle.HEAVY_CLICK)
    }

    private fun generateGlassCrackPcm(): ShortArray {
        val sampleRate = 44100
        val durationMs = 30
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        val random = java.util.Random(12345)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val freq1 = 4200f
            val freq2 = 6800f
            val noise = (random.nextFloat() * 2f - 1f) * 0.4f
            val env = Math.exp(-t.toDouble() * 600.0).toFloat()
            val sampleVal = ((sin(2f * Math.PI.toFloat() * freq1 * t) + sin(2f * Math.PI.toFloat() * freq2 * t) * 0.6f + noise) * env * 30000f).toInt()
            buffer[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateGlassShatterPcm(): ShortArray {
        val sampleRate = 44100
        val durationMs = 280
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        val random = java.util.Random(8888)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate

            // Sharp glass impact transient
            val impactEnv = Math.exp(-t.toDouble() * 110.0).toFloat()
            val impactNoise = (random.nextFloat() * 2f - 1f) * impactEnv * 0.75f

            // Realistic multi-tonal glass ringing harmonics
            val ringEnv = Math.exp(-t.toDouble() * 22.0).toFloat()
            val r1 = sin(2f * Math.PI.toFloat() * 2800f * t) * 0.35f
            val r2 = sin(2f * Math.PI.toFloat() * 4200f * t) * 0.30f
            val r3 = sin(2f * Math.PI.toFloat() * 6100f * t) * 0.25f
            val r4 = sin(2f * Math.PI.toFloat() * 7900f * t) * 0.20f
            val glassRing = (r1 + r2 + r3 + r4) * ringEnv

            // Clattering glass fragment bursts
            val scatterEnv = Math.exp(-t.toDouble() * 15.0).toFloat()
            val scatterNoise = (random.nextFloat() * 2f - 1f) * scatterEnv * 0.25f

            // Short smooth fade-out tail at the very end to prevent abrupt clipping
            val tailFade = if (i > numSamples - 1000) {
                (numSamples - i).toFloat() / 1000f
            } else 1.0f

            val mixed = (impactNoise + glassRing + scatterNoise) * tailFade
            val sampleVal = (mixed * 28000f).toInt()
            buffer[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateCableRetractPcm(): ShortArray {
        val sampleRate = 44100
        val durationMs = 250
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        val random = java.util.Random(9876)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val freq = 800f + (i.toFloat() / numSamples) * 1200f
            val noise = (random.nextFloat() * 2f - 1f) * 0.25f
            val env = (sin(Math.PI.toFloat() * (i.toFloat() / numSamples))).toFloat()
            val sampleVal = ((sin(2f * Math.PI.toFloat() * freq * t) * 0.4f + noise) * env * 24000f).toInt()
            buffer[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateLampLowerPcm(): ShortArray {
        val sampleRate = 44100
        val durationMs = 300
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        val random = java.util.Random(4321)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val freq = 1600f - (i.toFloat() / numSamples) * 1000f
            val noise = (random.nextFloat() * 2f - 1f) * 0.2f
            val env = (sin(Math.PI.toFloat() * (i.toFloat() / numSamples))).toFloat()
            val sampleVal = ((sin(2f * Math.PI.toFloat() * freq * t) * 0.5f + noise) * env * 25000f).toInt()
            buffer[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generatePowerOnPcm(): ShortArray {
        val sampleRate = 44100
        val durationMs = 150
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val freq1 = 220f + (i.toFloat() / numSamples) * 330f
            val freq2 = freq1 * 2f
            val env = Math.exp(-t.toDouble() * 18.0).toFloat() * (if (i < 500) i.toFloat() / 500f else 1f)
            val sampleVal = ((sin(2f * Math.PI.toFloat() * freq1 * t) * 0.6f + sin(2f * Math.PI.toFloat() * freq2 * t) * 0.3f) * env * 28000f).toInt()
            buffer[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateBrightnessTickPcm(): ShortArray {
        val sampleRate = 44100
        val durationMs = 6
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val startFreq = 3000f
            val endFreq = 2000f
            val freq = startFreq - (i.toFloat() / numSamples) * (startFreq - endFreq)
            val angle = 2f * Math.PI.toFloat() * freq * t
            
            // Fast exponential decay envelope
            val envelope = Math.exp(-t.toDouble() * 1500.0).toFloat()
            
            // Custom window to fade-in first 0.5ms and avoid popping/clipping artifacts
            val fadeIn = if (i < (sampleRate * 0.0005f)) {
                i.toFloat() / (sampleRate * 0.0005f)
            } else {
                1.0f
            }
            
            val sampleValue = (sin(angle.toDouble()).toFloat() * envelope * fadeIn * 32000f).toInt()
            buffer[i] = sampleValue.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateTogglePcm(): ShortArray {
        val sampleRate = 44100
        val durationMs = 40
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val startFreq = 1600f
            val endFreq = 500f
            val freq = startFreq - (i.toFloat() / numSamples) * (startFreq - endFreq)
            val angle = 2f * Math.PI.toFloat() * freq * t
            
            // Fast exponential decay envelope
            val envelope = Math.exp(-t.toDouble() * 180.0).toFloat()
            
            // Minor physical spring resonance at 150Hz
            val resonanceAngle = 2f * Math.PI.toFloat() * 150f * t
            val resonanceEnvelope = Math.exp(-t.toDouble() * 100.0).toFloat() * 0.15f
            
            val sampleValue = ((sin(angle.toDouble()).toFloat() + sin(resonanceAngle.toDouble()).toFloat() * resonanceEnvelope) * envelope * 32000f).toInt()
            buffer[i] = sampleValue.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateTickPcm(isStrong: Boolean): ShortArray {
        val sampleRate = 44100
        val durationMs = if (isStrong) 10 else 6
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val startFreq = if (isStrong) 1800f else 2500f
            val endFreq = if (isStrong) 1000f else 1500f
            val freq = startFreq - (i.toFloat() / numSamples) * (startFreq - endFreq)
            val angle = 2f * Math.PI.toFloat() * freq * t
            val envelope = Math.exp(-t.toDouble() * (if (isStrong) 500.0 else 800.0)).toFloat()
            val sampleValue = (sin(angle.toDouble()).toFloat() * envelope * 32000f).toInt()
            buffer[i] = sampleValue.coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun writeWavFile(file: File, pcmData: ShortArray) {
        val sampleRate = 44100
        val totalAudioLen = pcmData.size * 2
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * 2

        try {
            FileOutputStream(file).use { out ->
                val header = ByteArray(44)
                header[0] = 'R'.toByte() // RIFF
                header[1] = 'I'.toByte()
                header[2] = 'F'.toByte()
                header[3] = 'F'.toByte()
                header[4] = (totalDataLen and 0xff).toByte()
                header[5] = ((totalDataLen shr 8) and 0xff).toByte()
                header[6] = ((totalDataLen shr 16) and 0xff).toByte()
                header[7] = ((totalDataLen shr 24) and 0xff).toByte()
                header[8] = 'W'.toByte() // WAVE
                header[9] = 'A'.toByte()
                header[10] = 'V'.toByte()
                header[11] = 'E'.toByte()
                header[12] = 'f'.toByte() // fmt
                header[13] = 'm'.toByte()
                header[14] = 't'.toByte()
                header[15] = ' '.toByte()
                header[16] = 16 // size of fmt chunk
                header[17] = 0
                header[18] = 0
                header[19] = 0
                header[20] = 1 // format = 1 (PCM)
                header[21] = 0
                header[22] = 1 // channels = 1 (mono)
                header[23] = 0
                header[24] = (sampleRate and 0xff).toByte()
                header[25] = ((sampleRate shr 8) and 0xff).toByte()
                header[26] = ((sampleRate shr 16) and 0xff).toByte()
                header[27] = ((sampleRate shr 24) and 0xff).toByte()
                header[28] = (byteRate and 0xff).toByte()
                header[29] = ((byteRate shr 8) and 0xff).toByte()
                header[30] = ((byteRate shr 16) and 0xff).toByte()
                header[31] = ((byteRate shr 24) and 0xff).toByte()
                header[32] = 2 // block align
                header[33] = 0
                header[34] = 16 // bits per sample
                header[35] = 0
                header[36] = 'd'.toByte() // data
                header[37] = 'a'.toByte()
                header[38] = 't'.toByte()
                header[39] = 'a'.toByte()
                header[40] = (totalAudioLen and 0xff).toByte()
                header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
                header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
                header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

                out.write(header)
                val pcmBuffer = ByteBuffer.allocate(pcmData.size * 2)
                pcmBuffer.order(ByteOrder.LITTLE_ENDIAN)
                for (sample in pcmData) {
                    pcmBuffer.putShort(sample)
                }
                out.write(pcmBuffer.array())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

val LocalInteractionFeedbackManager = staticCompositionLocalOf<InteractionFeedbackManager> {
    error("No InteractionFeedbackManager provided")
}

@Composable
fun Modifier.clickableWithFeedback(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier {
    val feedbackManager = LocalInteractionFeedbackManager.current
    val view = LocalView.current
    return this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role
    ) {
        feedbackManager.playInteractionClick(view)
        onClick()
    }
}
