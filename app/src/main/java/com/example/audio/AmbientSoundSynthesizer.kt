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
    RAIN("Rain"),
    OCEAN("Ocean"),
    FIREPLACE("Fireplace"),
    FOREST("Forest"),
    CAFE("Cafe"),
    BROWN_NOISE("Brown Noise"),
    WHITE_NOISE("White Noise"),
    SOFT_PIANO("Soft Piano"),
    LO_FI("Lo-fi")
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

        // Variables for Soft Piano / Lo-Fi Generative chords
        val scaleFrequencies = floatArrayOf(
            130.81f, // C3
            146.83f, // D3
            164.81f, // E3
            196.00f, // G3
            220.00f, // A3
            261.63f, // C4
            293.66f, // D4
            329.63f, // E4
            392.00f, // G4
            440.00f, // A4
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
                    AmbientSoundType.WHITE_NOISE -> {
                        sample = random.nextFloat() * 2f - 1f
                    }

                    AmbientSoundType.BROWN_NOISE -> {
                        // Mathematically integrated random walk (Brownian motion)
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.98f) + white * 0.06f
                        sample = lastOutputValue * 3.5f // scale to standard amplitude
                    }

                    AmbientSoundType.OCEAN -> {
                        // Periodic swell using slow LFO sin wave (period approx 7s)
                        val swell = (sin(2.0 * PI * t / 7.0) + 1.0) / 2.0
                        // Add slightly higher frequency breathing component (period approx 3s)
                        val secondarySwell = (sin(2.0 * PI * t / 3.2) + 1.0) / 2.0 * 0.2
                        val totalSwell = (swell * 0.8 + secondarySwell * 0.2).coerceIn(0.0, 1.0)

                        // Smooth brown/pink-ish noise
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.95f) + white * 0.08f
                        sample = lastOutputValue * 2.5f * totalSwell.toFloat()
                    }

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

                    AmbientSoundType.FIREPLACE -> {
                        // Background low flame rumble (Brown Noise-like)
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.97f) + white * 0.06f
                        var fireBase = lastOutputValue * 1.5f

                        // Fire crackle impulses
                        if (crackleCountdown <= 0) {
                            if (random.nextFloat() < 0.003f) { // low chance, sudden crack
                                crackleCountdown = random.nextInt(200) + 50
                                crackleAmplitude = (random.nextFloat() * 0.8f + 0.2f)
                                crackleDecay = 0.92f
                            }
                        } else {
                            crackleCountdown--
                            crackleAmplitude *= crackleDecay
                            // Add crackling high-frequency pops
                            if (random.nextFloat() < 0.15f) {
                                fireBase += (if (random.nextBoolean()) 1f else -1f) * crackleAmplitude * 1.8f
                            }
                        }
                        sample = fireBase
                    }

                    AmbientSoundType.FOREST -> {
                        // Soft wind/rustle background
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.94f) + white * 0.08f
                        var forestBase = lastOutputValue * 0.6f

                        // Generative birds chirping (FM sweeps)
                        // Trigger a chirp sequence
                        if (secondPhase <= 0.0) {
                            if (random.nextFloat() < 0.0008f) { // rare bird calls
                                secondPhase = sampleRate * 0.35 // 350ms duration chirp
                            }
                        } else {
                            secondPhase--
                            val progress = 1.0 - (secondPhase / (sampleRate * 0.35))
                            // Bird frequency sweep (1500Hz upwards to 2100Hz with vibrato)
                            val chirpFreq = 1600.0 + 500.0 * progress + sin(2 * PI * progress * 15.0) * 100.0
                            val chirpEnvelope = sin(progress * PI).toFloat() // fade in/out
                            forestBase += (sin(2.0 * PI * chirpFreq * (progress * 0.35)) * 0.25 * chirpEnvelope).toFloat()
                        }
                        sample = forestBase
                    }

                    AmbientSoundType.CAFE -> {
                        // Low chatter murmur (combined slow frequency shifts)
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.92f) + white * 0.1f
                        var cafeBase = lastOutputValue * 0.8f

                        // Random clinks of porcelain/cups (high frequency resonators)
                        if (secondPhase <= 0.0) {
                            if (random.nextFloat() < 0.001f) { // cup clink
                                secondPhase = sampleRate * 0.15 // 150ms
                            }
                        } else {
                            secondPhase--
                            val progress = 1.0 - (secondPhase / (sampleRate * 0.15))
                            val clinkFreq = 2500.0 + 200.0 * sin(progress * 5.0)
                            val clinkDecay = exp(-progress * 8.0).toFloat()
                            cafeBase += (sin(2.0 * PI * clinkFreq * (progress * 0.15)) * 0.12 * clinkDecay).toFloat()
                        }
                        sample = cafeBase
                    }

                    AmbientSoundType.SOFT_PIANO -> {
                        // Ambient hum background
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.992f) + white * 0.015f
                        var soundBase = lastOutputValue * 0.2f

                        // Trigger a new soft note
                        pianoTick--
                        if (pianoTick <= 0) {
                            chordDelayTicks--
                            if (chordDelayTicks <= 0) {
                                // Decide chord scale
                                val noteCount = random.nextInt(2) + 1
                                for (n in 0 until noteCount) {
                                    val randomFreq = scaleFrequencies[random.nextInt(scaleFrequencies.size)]
                                    // Check if frequency is already active to avoid duplicates
                                    if (activeNotes.none { it.freq == randomFreq }) {
                                        activeNotes.add(
                                            ActiveNote(
                                                freq = randomFreq,
                                                time = 0.0,
                                                maxVolume = random.nextFloat() * 0.18f + 0.08f,
                                                decay = random.nextFloat() * 0.8f + 0.7f // slower decay
                                            )
                                        )
                                    }
                                }
                                chordDelayTicks = random.nextInt(4) + 2
                            }
                            pianoTick = random.nextInt(sampleRate * 2) + sampleRate * 2 // 2 to 4 seconds interval
                        }

                        // Render active notes
                        val iterator = activeNotes.iterator()
                        while (iterator.hasNext()) {
                            val note = iterator.next()
                            note.time += 1.0 / sampleRate
                            
                            // Exponential decay
                            val envelope = note.maxVolume * exp(-note.time * note.decay).toFloat()
                            if (envelope < 0.002f) {
                                iterator.remove()
                                continue
                            }

                            // Additive synthesis: fundamental + soft 2nd & 3rd harmonics for rich piano warmth
                            val fundamental = sin(2.0 * PI * note.freq * note.time)
                            val harmonic2 = sin(2.0 * PI * note.freq * 2.0 * note.time) * 0.35
                            val harmonic3 = sin(2.0 * PI * note.freq * 3.0 * note.time) * 0.15
                            
                            soundBase += ((fundamental + harmonic2 + harmonic3) * envelope).toFloat()
                        }
                        
                        sample = soundBase
                    }

                    AmbientSoundType.LO_FI -> {
                        // Low crackle floor + slow warm bass synth progress
                        val white = random.nextFloat() * 2f - 1f
                        lastOutputValue = (lastOutputValue * 0.96f) + white * 0.05f
                        var lofiBase = lastOutputValue * 0.3f // background rumble

                        // Sudden vinyl pops
                        if (random.nextFloat() < 0.0015f) {
                            lofiBase += (if (random.nextBoolean()) 1f else -1f) * (random.nextFloat() * 0.4f + 0.1f)
                        }

                        // Warm slow sub chords (e.g. Eb Major 7 to Ab Major 7 chords)
                        // Frequencies: Eb3(155.56), G3(196.00), Bb3(233.08), D4(293.66) and Ab2(103.83), C3(130.81), Eb3(155.56), G3(196.00)
                        val chord1 = floatArrayOf(155.56f, 196.00f, 233.08f, 293.66f)
                        val chord2 = floatArrayOf(103.83f, 130.81f, 155.56f, 196.00f)
                        
                        val cycleDuration = sampleRate * 12 // 12 seconds per chord
                        val currentCycle = (phase.toLong() % (cycleDuration * 2))
                        val isFirstChord = currentCycle < cycleDuration
                        val activeChord = if (isFirstChord) chord1 else chord2

                        // Amplitude envelope for the chord (slow fade in, sustained, slow fade out)
                        val cyclePosition = currentCycle % cycleDuration
                        val ratio = cyclePosition.toDouble() / cycleDuration
                        val volumeEnvelope = when {
                            ratio < 0.2 -> ratio / 0.2 // Fade in
                            ratio > 0.8 -> (1.0 - ratio) / 0.2 // Fade out
                            else -> 1.0 // Sustain
                        } * 0.15 // volume scale

                        for (freq in activeChord) {
                            lofiBase += (sin(2.0 * PI * freq * (cyclePosition.toDouble() / sampleRate)) * volumeEnvelope).toFloat()
                        }

                        sample = lofiBase
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
