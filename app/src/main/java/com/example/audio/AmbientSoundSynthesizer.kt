package com.example.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class AmbientSoundType(val displayName: String) {
    NONE("None"),
    RAIN("🌧 Rain"),
    OCEAN("🌊 Ocean"),
    NATURAL("🌿 Natural"),
    MEDITATION("🧘 Meditation"),
    DEEP_SOUND("🌌 Deep Sound"),
    BELL("🔔 Bell")
}

class AmbientSoundSynthesizer {
    private var audioTrack: AudioTrack? = null
    private var synthThread: Thread? = null
    @Volatile private var isRunning = false
    @Volatile private var currentType = AmbientSoundType.NONE
    @Volatile private var masterVolume = 0.5f

    private val sampleRate = 22050 // 22.05kHz is plenty for high-quality ambient sounds and saves CPU
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    fun start(type: AmbientSoundType, volume: Float) {
        if (currentType == type && isRunning) {
            setVolume(volume)
            return
        }
        stop()
        if (type == AmbientSoundType.NONE) return

        currentType = type
        masterVolume = volume
        isRunning = true

        try {
            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            audioTrack?.setStereoVolume(masterVolume, masterVolume)
            audioTrack?.play()

            synthThread = Thread {
                generateAudioLoop()
            }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        } catch (e: Exception) {
            Log.e("SoundSynth", "Error starting synthesizer", e)
        }
    }

    fun setVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        try {
            audioTrack?.setStereoVolume(masterVolume, masterVolume)
        } catch (e: Exception) {
            Log.e("SoundSynth", "Error updating volume", e)
        }
    }

    fun stop() {
        isRunning = false
        synthThread?.interrupt()
        synthThread = null
        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e("SoundSynth", "Error stopping audio", e)
        } finally {
            audioTrack = null
            currentType = AmbientSoundType.NONE
        }
    }

    private fun generateAudioLoop() {
        val random = Random()
        val shortBuffer = ShortArray(1024)
        
        // State variables for filters and generation
        var lastOutputValue = 0f // Filter state for noise
        var phase = 0.0
        var secondPhase = 0.0

        // Variables for Rain / Fireplace crackles
        var crackleCountdown = 0
        var crackleAmplitude = 0f
        var crackleDecay = 0.95f

        // Frequencies for Meditation (Pentatonic scales) & Bell (Overtones)
        val medFrequencies = floatArrayOf(
            174.61f, // F3
            220.00f, // A3
            261.63f, // C4
            293.66f, // D4
            329.63f, // E4
            349.23f, // F4
            440.00f  // A4
        )
        val bellFrequencies = floatArrayOf(
            261.63f, // C4
            329.63f, // E4
            392.00f, // G4
            432.00f, // A4 (432Hz tuning)
            523.25f  // C5
        )

        class ActiveNote(val freq: Float, var time: Double, val maxVolume: Float, val decay: Float)
        val activeNotes = mutableListOf<ActiveNote>()
        var pianoTick = 0
        var chordDelayTicks = 0

        // Main PCM feeding loop
        while (isRunning) {
            val type = currentType
            if (type == AmbientSoundType.NONE) break

            for (i in shortBuffer.indices) {
                var sample = 0f
                val t = phase / sampleRate

                when (type) {
                    AmbientSoundType.RAIN -> {
                        // Base constant soft noise (rumble of falling rain)
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.88f) + white * 0.12f
                        var rainBase = lastOutputValue * 1.2f

                        // Add individual patters/drops
                        if (crackleCountdown <= 0) {
                            if (random.nextFloat() < 0.015f) { // raindrop chance
                                crackleCountdown = random.nextInt(120) + 10
                                crackleAmplitude = (random.nextFloat() * 0.5f + 0.1f)
                                crackleDecay = random.nextFloat() * 0.08f + 0.85f // decay rate
                            }
                        } else {
                            crackleCountdown--
                            crackleAmplitude *= crackleDecay
                            rainBase += (random.nextFloat() * 2f - 1f) * crackleAmplitude
                        }
                        sample = rainBase
                    }

                    AmbientSoundType.OCEAN -> {
                        // Periodic swell using slow LFO sin wave (period approx 7s)
                        val swell = (sin(2.0 * PI * t / 7.0) + 1.0) / 2.0
                        val secondarySwell = (sin(2.0 * PI * t / 3.2) + 1.0) / 2.0 * 0.2
                        val totalSwell = (swell * 0.8 + secondarySwell * 0.2).coerceIn(0.0, 1.0)

                        // Smooth brown/pink-ish noise
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.95f) + white * 0.08f
                        sample = lastOutputValue * 2.5f * totalSwell.toFloat()
                    }

                    AmbientSoundType.NATURAL -> {
                        // Soft wind/rustle background
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.94f) + white * 0.08f
                        var naturalBase = lastOutputValue * 0.6f

                        // Birds chirping / breeze FM sweeps
                        if (secondPhase <= 0.0) {
                            if (random.nextFloat() < 0.0008f) { // bird call
                                secondPhase = sampleRate * 0.35 // 350ms duration
                            }
                        } else {
                            secondPhase--
                            val progress = 1.0 - (secondPhase / (sampleRate * 0.35))
                            val chirpFreq = 1600.0 + 500.0 * progress + sin(2 * PI * progress * 15.0) * 100.0
                            val chirpEnvelope = sin(progress * PI).toFloat()
                            naturalBase += (sin(2.0 * PI * chirpFreq * (progress * 0.35)) * 0.25 * chirpEnvelope).toFloat()
                        }
                        sample = naturalBase
                    }

                    AmbientSoundType.MEDITATION -> {
                        // Soft warm pad background
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.992f) + white * 0.015f
                        var soundBase = lastOutputValue * 0.15f

                        // Generative meditation tones
                        pianoTick--
                        if (pianoTick <= 0) {
                            chordDelayTicks--
                            if (chordDelayTicks <= 0) {
                                val noteCount = random.nextInt(2) + 1
                                for (n in 0 until noteCount) {
                                    val randomFreq = medFrequencies[random.nextInt(medFrequencies.size)]
                                    if (activeNotes.none { it.freq == randomFreq }) {
                                        activeNotes.add(
                                            ActiveNote(
                                                freq = randomFreq,
                                                time = 0.0,
                                                maxVolume = random.nextFloat() * 0.15f + 0.08f,
                                                decay = random.nextFloat() * 0.4f + 0.3f
                                            )
                                        )
                                    }
                                }
                                chordDelayTicks = random.nextInt(4) + 2
                            }
                            pianoTick = random.nextInt(sampleRate * 3) + sampleRate * 2
                        }

                        val iterator = activeNotes.iterator()
                        while (iterator.hasNext()) {
                            val note = iterator.next()
                            note.time += 1.0 / sampleRate
                            val envelope = note.maxVolume * exp(-note.time * note.decay).toFloat()
                            if (envelope < 0.002f) {
                                iterator.remove()
                                continue
                            }
                            val fundamental = sin(2.0 * PI * note.freq * note.time)
                            val harmonic2 = sin(2.0 * PI * note.freq * 2.0 * note.time) * 0.25
                            soundBase += ((fundamental + harmonic2) * envelope).toFloat()
                        }
                        sample = soundBase
                    }

                    AmbientSoundType.DEEP_SOUND -> {
                        // Low-frequency atmospheric drones (55Hz, 110Hz, 165Hz)
                        val lfo1 = (sin(2.0 * PI * t / 8.0) * 0.2 + 0.8)
                        val lfo2 = (sin(2.0 * PI * t / 11.0) * 0.2 + 0.8)

                        val subBass = sin(2.0 * PI * 55.0 * t) * 0.4 * lfo1
                        val bassTone = sin(2.0 * PI * 110.0 * t) * 0.3 * lfo2
                        val fifthTone = sin(2.0 * PI * 165.0 * t) * 0.15 * lfo1
                        val octaveTone = sin(2.0 * PI * 220.0 * t) * 0.08 * lfo2

                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.985f) + white * 0.02f

                        sample = ((subBass + bassTone + fifthTone + octaveTone) * 0.7f + lastOutputValue * 0.15f).toFloat()
                    }

                    AmbientSoundType.BELL -> {
                        // Gentle Tibetan bell ambience
                        pianoTick--
                        if (pianoTick <= 0) {
                            val bellFreq = bellFrequencies[random.nextInt(bellFrequencies.size)]
                            activeNotes.clear()
                            activeNotes.add(
                                ActiveNote(
                                    freq = bellFreq,
                                    time = 0.0,
                                    maxVolume = 0.35f,
                                    decay = 0.25f
                                )
                            )
                            pianoTick = sampleRate * 6
                        }

                        var bellSample = 0f
                        val iterator = activeNotes.iterator()
                        while (iterator.hasNext()) {
                            val note = iterator.next()
                            note.time += 1.0 / sampleRate
                            val envelope = note.maxVolume * exp(-note.time * note.decay).toFloat()
                            if (envelope < 0.001f) {
                                iterator.remove()
                                continue
                            }
                            val f = note.freq
                            val fundamental = sin(2.0 * PI * f * note.time)
                            val overtone1 = sin(2.0 * PI * f * 2.76 * note.time) * 0.35
                            val overtone2 = sin(2.0 * PI * f * 5.40 * note.time) * 0.15
                            bellSample += ((fundamental + overtone1 + overtone2) * envelope).toFloat()
                        }

                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.99f) + white * 0.01f

                        sample = bellSample + lastOutputValue * 0.05f
                    }

                    else -> {
                        sample = 0f
                    }
                }

                // Smoothly limit (soft clip) to prevent digital distortion
                val clippedSample = when {
                    sample > 1.0f -> 0.75f + 0.25f * (sample - 1.0f) / (1.0f + (sample - 1.0f))
                    sample < -1.0f -> -0.75f + 0.25f * (sample + 1.0f) / (1.0f - (sample + 1.0f))
                    else -> sample
                }

                // Scale to 16-bit PCM integer
                shortBuffer[i] = (clippedSample * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                phase++
            }

            // Write chunk to AudioTrack
            try {
                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
            } catch (e: Exception) {
                Log.e("SoundSynth", "Write error", e)
                break
            }
        }
    }
}
