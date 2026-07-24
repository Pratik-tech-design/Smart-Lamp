package com.example

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.components.BottomSheetControls
import com.example.ui.components.HangingLamp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LampViewModel
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.foundation.layout.offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.roundToInt
import android.media.SoundPool
import android.media.AudioAttributes
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay

import androidx.compose.runtime.CompositionLocalProvider
import com.example.audio.InteractionFeedbackManager
import com.example.audio.LocalInteractionFeedbackManager

class MainActivity : ComponentActivity() {
    private val viewModel: LampViewModel by viewModels()
    private var originalBrightness: Float = -1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Save the current hardware screen brightness setting on startup
        try {
            val sysBrightness = android.provider.Settings.System.getInt(
                contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS
            )
            originalBrightness = sysBrightness / 255f
            
            // Perfectly synchronize our initial brightness with the actual screen brightness
            viewModel.setBrightness(originalBrightness.coerceIn(0.01f, 1f))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        enableEdgeToEdge()
        setContent {
            val feedbackManager = remember { InteractionFeedbackManager(applicationContext) }
            MyApplicationTheme {
                CompositionLocalProvider(
                    LocalInteractionFeedbackManager provides feedbackManager
                ) {
                    SmartLampAppScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically check if the system-write-settings permission was granted
        viewModel.checkWriteSettingsPermission(this)
    }

    override fun onPause() {
        super.onPause()
        // Automatically restore user's original display brightness when leaving
        restoreOriginalBrightness()
    }

    override fun onDestroy() {
        super.onDestroy()
        restoreOriginalBrightness()
    }

    fun restoreOriginalBrightness() {
        if (originalBrightness >= 0f) {
            try {
                val layoutParams = window.attributes
                layoutParams.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = layoutParams
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

fun getBaseRoomColor(darkness: Float, lampBrightness: Float, lampColor: Color): Color {
    val d = darkness.coerceIn(0f, 1f)
    val b = lampBrightness.coerceIn(0f, 1f)
    
    // Perceptual non-linear brightness curve (cubic)
    val bFactor = b * b * b
    
    // Base wall color (when lamp is OFF and room is light/dark)
    val baseR = 0.93f * (1f - d) + 0.015f * d
    val baseG = 0.94f * (1f - d) + 0.015f * d
    val baseB = 0.96f * (1f - d) + 0.02f * d
    
    // Blend lamp color when it is ON and bright
    val illuminatedR = baseR * (1f - bFactor) + (baseR + lampColor.red * 0.42f).coerceIn(0f, 1f) * bFactor
    val illuminatedG = baseG * (1f - bFactor) + (baseG + lampColor.green * 0.42f).coerceIn(0f, 1f) * bFactor
    val illuminatedB = baseB * (1f - bFactor) + (baseB + lampColor.blue * 0.40f).coerceIn(0f, 1f) * bFactor
    
    // Ensure that when d is high and b is 0 (dark room, lamp off), background hits deep rich blacks
    val finalR = illuminatedR * (0.02f + 0.98f * (1f - d * (1f - bFactor)))
    val finalG = illuminatedG * (0.02f + 0.98f * (1f - d * (1f - bFactor)))
    val finalB = illuminatedB * (0.02f + 0.98f * (1f - d * (1f - bFactor)))

    return Color(
        red = finalR.coerceIn(0f, 1f),
        green = finalG.coerceIn(0f, 1f),
        blue = finalB.coerceIn(0f, 1f)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartLampAppScreen(
    viewModel: LampViewModel,
    modifier: Modifier = Modifier
) {
    val isOn by viewModel.isOn.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val roomDarkness by viewModel.roomDarkness.collectAsState()
    val isFocusMode by viewModel.isFocusMode.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val activePresetName by viewModel.activePresetName.collectAsState()
    val view = LocalView.current
    val context = LocalContext.current

    // Bulb position collected from ViewModel for high-fidelity radial light falloff
    val bulbPosition by viewModel.bulbPosition.collectAsState()

    // Device actual screen brightness control
    val systemBrightnessSync by viewModel.systemBrightnessSync.collectAsState()
    val hasWriteSettingsPermission by viewModel.hasWriteSettingsPermission.collectAsState()

    LaunchedEffect(brightness, isOn, systemBrightnessSync, hasWriteSettingsPermission) {
        val targetBrightness = if (isOn) brightness.coerceIn(0.01f, 1f) else 0.01f
        
        // 1. Instantly update the Window screen brightness override (60 FPS, zero delay, no permission required)
        val activity = context as? android.app.Activity
        activity?.let { act ->
            try {
                val layoutParams = act.window.attributes
                layoutParams.screenBrightness = targetBrightness
                act.window.attributes = layoutParams
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. If System Brightness Sync is enabled and we have the WRITE_SETTINGS permission, also update system-wide brightness
        if (systemBrightnessSync && hasWriteSettingsPermission) {
            try {
                android.provider.Settings.System.putInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    (targetBrightness * 255).toInt().coerceIn(1, 255)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Sleep Timer States for Fullscreen Overlays and Compact Chips
    val timerSecondsLeft by viewModel.timerSecondsLeft.collectAsState()
    val timerDurationSeconds by viewModel.timerDurationSeconds.collectAsState()

    var showFullTimerOverlay by remember { mutableStateOf(false) }
    var lastActiveSeconds by remember { mutableStateOf(-1L) }

    // Display duration overlay on start or extend
    LaunchedEffect(timerSecondsLeft) {
        val seconds = timerSecondsLeft
        if (seconds > 0) {
            if (lastActiveSeconds <= 0 || seconds > lastActiveSeconds + 2) {
                showFullTimerOverlay = true
                delay(3000)
                showFullTimerOverlay = false
            }
        } else {
            showFullTimerOverlay = false
        }
        lastActiveSeconds = seconds
    }

    // Bottom sheet expand state
    var isSheetExpanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val isAdjustingBrightnessVM by viewModel.isAdjustingBrightness.collectAsState()
    var isHUDVisible by remember { mutableStateOf(false) }
    var localIsAdjustingBrightness by remember { mutableStateOf(false) }
    var scrollAdjustJob by remember { mutableStateOf<Job?>(null) }

    // Dynamic coordination of HUD visibility based on local drag and VM state
    val isAnyBrightnessAdjusting = localIsAdjustingBrightness || isAdjustingBrightnessVM
    LaunchedEffect(isAnyBrightnessAdjusting) {
        if (isAnyBrightnessAdjusting) {
            isHUDVisible = true
        } else {
            isHUDVisible = false
        }
    }

    // Precise Haptic and Audio Tick feedback synchronized for rotary dial crossings
    val feedbackManager = LocalInteractionFeedbackManager.current
    val numTicks = 45
    val tickIndex = (brightness * (numTicks - 1)).roundToInt()
    var lastTickIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(tickIndex) {
        if (lastTickIndex != -1 && tickIndex != lastTickIndex) {
            if (isAnyBrightnessAdjusting) {
                feedbackManager.playBrightnessTick(view, brightness)
            }
        }
        lastTickIndex = tickIndex
    }

    // Floating Exit button auto-hide timer
    var showExitButton by remember { mutableStateOf(false) }

    LaunchedEffect(isFocusMode) {
        if (isFocusMode) {
            showExitButton = true
            delay(2000)
            showExitButton = false
        }
    }

    LaunchedEffect(showExitButton) {
        if (showExitButton) {
            delay(2000)
            showExitButton = false
        }
    }

    // Immersive Mode (hide/show status & nav bars)
    val activity = remember(context) { context as? ComponentActivity }
    val window = remember(activity) { activity?.window }

    LaunchedEffect(isFocusMode, window) {
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (isFocusMode) {
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    // Dynamic background and typography colors responding live to room darkness
    val isAmbientDark by viewModel.isAmbientDark.collectAsState()

    val animatedRoomDarkness by animateFloatAsState(
        targetValue = roomDarkness,
        animationSpec = tween(400),
        label = "room_darkness"
    )

    // Smoothly animated lamp brightness for perfect, latency-free ON/OFF and brightness transitions
    val animatedLampBrightness by animateFloatAsState(
        targetValue = if (isOn) brightness else 0f,
        animationSpec = tween(250),
        label = "animated_lamp_brightness"
    )

    // UI elements exposure/dimming factor
    // When room is bright (animatedRoomDarkness is low), UI is fully bright.
    // When room is dark, UI is dimmed when lamp is low, and fully bright when lamp is high.
    val uiDimFactor by animateFloatAsState(
        targetValue = if (isAmbientDark) {
            0.4f + 0.6f * animatedLampBrightness
        } else {
            1.0f
        },
        animationSpec = tween(250),
        label = "ui_dim_factor"
    )

    // Play premium ON/OFF sound with haptic when power toggles
    var isFirstPowerCheck by remember { mutableStateOf(true) }
    LaunchedEffect(isOn) {
        if (isFirstPowerCheck) {
            isFirstPowerCheck = false
        } else {
            feedbackManager.playToggleSound(view)
        }
    }

    val baseColor = getBaseRoomColor(animatedRoomDarkness, animatedLampBrightness, selectedColor)

    val textColor by animateColorAsState(
        targetValue = if (animatedRoomDarkness < 0.5f) Color(0xFF111111) else Color(0xFFF7F8FA),
        animationSpec = tween(400),
        label = "text_color"
    )

    val secondaryTextColor by animateColorAsState(
        targetValue = if (animatedRoomDarkness < 0.5f) Color(0xFF6B7280) else Color(0xFF9EAFBF),
        animationSpec = tween(400),
        label = "secondary_text_color"
    )

    val density = LocalDensity.current
    val falloffRadiusPx = with(density) { 500.dp.toPx() }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // 1. Draw solid room base color (goes down to pure black at 100% room darkness)
                    drawRect(color = baseColor)

                    // 2. Realistic Radial Light Falloff from the bulb center
                    if (animatedLampBrightness > 0.01f) {
                        val center = bulbPosition ?: Offset(size.width / 2f, size.height * 0.4f)

                        // Scale falloff radius with brightness
                        val dynamicRadius = falloffRadiusPx * (0.65f + 0.35f * animatedLampBrightness)

                        // Center illumination intensity depends on brightness and room darkness
                        val intensity = animatedLampBrightness * (0.35f + 0.45f * animatedRoomDarkness)

                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    selectedColor.copy(alpha = intensity * 0.7f),
                                    selectedColor.copy(alpha = intensity * 0.3f),
                                    selectedColor.copy(alpha = intensity * 0.08f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = dynamicRadius
                            )
                        )
                    }
                }
                .pointerInput(isFocusMode) {
                    if (!isFocusMode) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (deltaY != 0f) {
                                    val change = -deltaY * 0.02f
                                    viewModel.setBrightness((viewModel.brightness.value + change).coerceIn(0.01f, 1f))
                                    scrollAdjustJob?.cancel()
                                    localIsAdjustingBrightness = true
                                    scrollAdjustJob = coroutineScope.launch {
                                        delay(800)
                                        localIsAdjustingBrightness = false
                                        feedbackManager.stopBrightnessTicks()
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(isFocusMode) {
                    if (!isFocusMode) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            val startX = firstDown.position.x
                            val startY = firstDown.position.y
                            val pointerId = firstDown.id
                            var isTap = true
                            var dragActive = true
                            
                            while (dragActive) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null || !change.pressed) {
                                    dragActive = false
                                } else {
                                    val dist = sqrt((change.position.x - startX).pow(2) + (change.position.y - startY).pow(2))
                                    if (dist > 12.dp.toPx()) {
                                        isTap = false
                                    }
                                }
                            }
                            
                            if (isTap) {
                                showExitButton = !showExitButton
                            }
                        }
                    }
                }
                .testTag("app_main_background")
        ) {
            // Screen contents
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Header Bar (Apple Home Inspired)
                AnimatedVisibility(
                    visible = !isFocusMode,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    SmartLampHeader(
                        presetName = activePresetName ?: "Soft White",
                        isOn = isOn,
                        textColor = textColor,
                        isAmbientDark = isAmbientDark,
                        onThemeToggle = { viewModel.toggleAmbientDark() },
                        onFocusToggle = { viewModel.toggleFocusMode() },
                        modifier = Modifier.graphicsLayer { alpha = uiDimFactor }
                    )
                }

                // Hanging Lamp Component (placed at top center)
                HangingLamp(
                    viewModel = viewModel,
                    onTap = { isSheetExpanded = !isSheetExpanded },
                    modifier = Modifier.weight(1f)
                )

                // Bottom padding to avoid overlapping the collapsed bottom sheet
                AnimatedVisibility(
                    visible = !isFocusMode,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Spacer(modifier = Modifier.height(180.dp))
                }
            }

            // Expandable Bottom Sheet (anchored at the very bottom)
            AnimatedVisibility(
                visible = !isFocusMode,
                enter = fadeIn() + slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400)
                ),
                exit = fadeOut() + slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(400)
                ),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomSheetControls(
                    viewModel = viewModel,
                    isExpanded = isSheetExpanded,
                    onToggleExpand = { isSheetExpanded = !isSheetExpanded },
                    modifier = Modifier
                        .navigationBarsPadding()
                )
            }

            // Bottom edge swipe up gesture detector in Focus Mode to bring back bottom sheet
            if (isFocusMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.BottomCenter)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (dragAmount.y < -12f) {
                                    viewModel.setFocusMode(false)
                                }
                            }
                        }
                )
            }

            // Premium Semicircular Brightness Dial (replaces the circular dial)
            val hudAlpha by animateFloatAsState(
                targetValue = if (isHUDVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "hud_alpha"
            )
            val hudOffsetX by animateDpAsState(
                targetValue = if (isHUDVisible) 0.dp else 40.dp,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "hud_offset_x"
            )
            val hudScale by animateFloatAsState(
                targetValue = if (isHUDVisible) 1f else 0.95f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "hud_scale"
            )

            if (hudAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer {
                            alpha = hudAlpha
                            translationX = hudOffsetX.toPx()
                            scaleX = hudScale
                            scaleY = hudScale
                        }
                        .testTag("premium_brightness_hud")
                ) {
                    PremiumSemicircularBrightnessDial(
                        brightness = brightness,
                        selectedColor = selectedColor,
                        isAmbientDark = isAmbientDark
                    )
                }
            }

            // Floating Brightness Handle (always visible in Focus Mode)
            val handleTargetAlpha = if (isHUDVisible || localIsAdjustingBrightness || isAdjustingBrightnessVM) 1f else 0.5f
            val handleAlpha by animateFloatAsState(
                targetValue = handleTargetAlpha,
                animationSpec = tween(
                    durationMillis = if (handleTargetAlpha == 1f) 150 else 200,
                    easing = FastOutSlowInEasing
                ),
                label = "handle_alpha"
            )

            AnimatedVisibility(
                visible = isFocusMode,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(400)),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(60.dp)
                        .height(80.dp)
                        .graphicsLayer {
                            alpha = handleAlpha
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val downEvent = awaitFirstDown(requireUnconsumed = false)
                                    val pointerId = downEvent.id
                                    var isHeld = false
                                    var lastY = downEvent.position.y
                                    
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    
                                    val holdJob = coroutineScope.launch {
                                        delay(250) // 250ms hold threshold
                                        isHeld = true
                                        localIsAdjustingBrightness = true
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                    
                                    var dragActive = true
                                    while (dragActive) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                        if (change == null || !change.pressed) {
                                            dragActive = false
                                        } else {
                                            val currentY = change.position.y
                                            val diffY = currentY - lastY
                                            
                                            if (isHeld) {
                                                change.consume()
                                                val dragRangePx = 280.dp.toPx()
                                                val brightnessChange = -diffY / dragRangePx
                                                val newBrightness = (viewModel.brightness.value + brightnessChange).coerceIn(0.01f, 1f)
                                                viewModel.setBrightness(newBrightness)
                                            } else {
                                                val dist = Math.abs(currentY - downEvent.position.y)
                                                if (dist > 15.dp.toPx()) {
                                                    holdJob.cancel()
                                                }
                                            }
                                            lastY = currentY
                                        }
                                    }
                                    
                                    holdJob.cancel()
                                    if (isHeld) {
                                        localIsAdjustingBrightness = false
                                        feedbackManager.stopBrightnessTicks()
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    } else {
                                        localIsAdjustingBrightness = false
                                        feedbackManager.stopBrightnessTicks()
                                    }
                                }
                            }
                        }
                        .testTag("floating_brightness_handle")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 26.dp)
                            .size(52.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.15f),
                                spotColor = Color.Black.copy(alpha = 0.25f)
                            )
                            .background(
                                color = if (isAmbientDark) Color(0xFF1E212B).copy(alpha = 0.8f)
                                        else Color.White.copy(alpha = 0.8f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = if (isAmbientDark) Color.White.copy(alpha = 0.15f)
                                        else Color.Black.copy(alpha = 0.12f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WbSunny,
                            contentDescription = "Adjust Brightness",
                            tint = selectedColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Floating circular Back button in the top-left corner during Focus Mode
            AnimatedVisibility(
                visible = isFocusMode,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(400)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 16.dp)
            ) {
                IconButton(
                    onClick = {
                        feedbackManager.playInteractionClick(view)
                        viewModel.setFocusMode(false)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isAmbientDark) Color.Black.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.25f)
                        )
                        .testTag("focus_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Exit Focus Mode",
                        tint = if (isAmbientDark) Color.White else Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Optional Exit button in Focus Mode
            AnimatedVisibility(
                visible = isFocusMode && showExitButton,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            ) {
                Button(
                    onClick = {
                        feedbackManager.playInteractionClick(view)
                        viewModel.setFocusMode(false)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("exit_focus_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Exit Focus Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 1. Fullscreen Timer Overlay (fades out after 3 seconds)
            AnimatedVisibility(
                visible = timerSecondsLeft > 0 && showFullTimerOverlay,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.9f),
                exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "🌙 Sleep Timer • ${formatTime(timerSecondsLeft)} remaining",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 2. Compact Timer Chip (visible in top-right corner after full overlay fades)
            var showTimerMenu by remember { mutableStateOf(false) }

            AnimatedVisibility(
                visible = timerSecondsLeft > 0 && !showFullTimerOverlay,
                enter = fadeIn(animationSpec = tween(350)),
                exit = fadeOut(animationSpec = tween(350)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = if (isFocusMode) 24.dp else 84.dp,
                        end = 24.dp
                    )
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .combinedClickable(
                                onClick = {
                                    feedbackManager.playInteractionClick(view)
                                    // Expand bottom sheet controls to show timer details
                                    viewModel.setFocusMode(false)
                                    isSheetExpanded = true
                                },
                                onLongClick = {
                                    feedbackManager.playInteractionClick(view)
                                    showTimerMenu = true
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "⏱",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatTime(timerSecondsLeft),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTimerMenu,
                        onDismissRequest = { showTimerMenu = false },
                        modifier = Modifier.background(Color(0xFF1E2025))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Extend by 15 min", color = Color.White) },
                            onClick = {
                                feedbackManager.playInteractionClick(view)
                                viewModel.extendTimer(15)
                                showTimerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Extend by 30 min", color = Color.White) },
                            onClick = {
                                feedbackManager.playInteractionClick(view)
                                viewModel.extendTimer(30)
                                showTimerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel Timer", color = Color(0xFFFF5252)) },
                            onClick = {
                                feedbackManager.playInteractionClick(view)
                                viewModel.cancelTimer()
                                showTimerMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Custom M3 system screen brightness permission request dialog
        val showPermissionDialog by viewModel.showPermissionDialog.collectAsState()
        if (showPermissionDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.setShowPermissionDialog(false) },
                title = {
                    Text(
                        text = "System Brightness Sync",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = "To perfectly synchronize your physical room with the hanging lamp, we need permission to modify system settings. This allows the lamp's brightness slider to directly control your phone's screen brightness, simulating a real light bulb!\n\nWithout this permission, we will still adjust your screen brightness while you are using the app, but settings-wide synchronization won't be available.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.setShowPermissionDialog(false)
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                ).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Text("Grant Permission", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.setShowPermissionDialog(false) }
                    ) {
                        Text("Keep Window-Only", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                textContentColor = Color.White,
                titleContentColor = Color.White
            )
        }
    }
}

fun formatTime(secondsLeft: Long): String {
    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun SmartLampHeader(
    presetName: String,
    isOn: Boolean,
    textColor: Color,
    isAmbientDark: Boolean,
    onThemeToggle: () -> Unit,
    onFocusToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null
) {
    val feedbackManager = LocalInteractionFeedbackManager.current
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button (shown only on secondary/full-screen pages)
        if (showBackButton) {
            IconButton(
                onClick = {
                    feedbackManager.playInteractionClick(view)
                    onBackClick?.invoke()
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(textColor.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        // Title text
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "AfterDark",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                letterSpacing = (-0.5).sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = "Room indicator",
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Bedroom • $presetName",
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }

        // Focus Mode button (cinematic full-screen trigger)
        IconButton(
            onClick = {
                feedbackManager.playInteractionClick(view)
                onFocusToggle()
            },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(textColor.copy(alpha = 0.08f))
                .testTag("focus_mode_header_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.CenterFocusStrong,
                contentDescription = "Focus Mode",
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Dynamic Light/Dark Theme Toggle Button (replaces Settings Button)
        IconButton(
            onClick = {
                feedbackManager.playInteractionClick(view)
                onThemeToggle()
            },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(textColor.copy(alpha = 0.08f))
                .testTag("theme_toggle_button")
        ) {
            Icon(
                imageVector = if (isAmbientDark) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay,
                contentDescription = if (isAmbientDark) "Switch to Light Mode" else "Switch to Dark Mode",
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PremiumSemicircularBrightnessDial(
    brightness: Float,
    selectedColor: Color,
    isAmbientDark: Boolean,
    modifier: Modifier = Modifier
) {
    val displayPercent = (brightness * 100).toInt()
    
    // Fine-grained continuous rotation or pulsing animation for a highly dynamic premium feel
    val infiniteTransition = rememberInfiniteTransition(label = "ticks_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier
            .width(130.dp)
            .height(260.dp)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // The center of our 180-degree semicircle sits at the right edge
            val center = Offset(size.width, size.height / 2f)
            val outerRadius = size.width - 12.dp.toPx()
            val innerRadius = outerRadius - 16.dp.toPx()
            val indicatorRadius = (innerRadius + outerRadius) / 2f
            
            val numTicks = 45 // Many thin radial tick marks like the reference image
            val activeColor = selectedColor
            val inactiveColor = if (isAmbientDark) Color.White.copy(alpha = 0.12f)
                                else Color.Black.copy(alpha = 0.10f)
            
            // Draw 180 degrees arc spanning from 90 degrees (bottom) to 270 degrees (top)
            for (i in 0 until numTicks) {
                val tickRatio = i.toFloat() / (numTicks - 1)
                // Bottom is 90 deg, middle is 180 deg, top is 270 deg (counter-clockwise)
                val tickAngleDegrees = 90f + tickRatio * 180f
                val isActive = tickRatio <= brightness
                
                val angleRad = Math.toRadians(tickAngleDegrees.toDouble())
                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()
                
                val start = Offset(
                    x = center.x + innerRadius * cosA,
                    y = center.y + innerRadius * sinA
                )
                val end = Offset(
                    x = center.x + outerRadius * cosA,
                    y = center.y + outerRadius * sinA
                )
                
                // Active ticks can have a subtle pulse animation
                val color = if (isActive) {
                    activeColor.copy(alpha = pulseAlpha)
                } else {
                    inactiveColor
                }
                
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = if (isActive) 2.dp.toPx() else 1.25.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            
            // Smooth moving active indicator dot along the arc
            val currentAngleRad = Math.toRadians((90f + brightness * 180f).toDouble())
            val indicatorX = center.x + indicatorRadius * cos(currentAngleRad).toFloat()
            val indicatorY = center.y + indicatorRadius * sin(currentAngleRad).toFloat()
            
            // Highlighted Indicator Glow and Circle
            drawCircle(
                color = selectedColor.copy(alpha = 0.4f),
                radius = 7.dp.toPx(),
                center = Offset(indicatorX, indicatorY)
            )
            drawCircle(
                color = Color.White,
                radius = 3.5.dp.toPx(),
                center = Offset(indicatorX, indicatorY)
            )
        }
        
        // Content container inside the semicircle (centered relative to the dial)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp) // Sits beautifully inside the arc
        ) {
            Icon(
                imageVector = Icons.Rounded.WbSunny,
                contentDescription = "Brightness Icon",
                tint = selectedColor.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        // Subtle scale and spin based on brightness
                        scaleX = 0.85f + brightness * 0.3f
                        scaleY = 0.85f + brightness * 0.3f
                    }
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "$displayPercent%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAmbientDark) Color.White else Color(0xFF1E212B),
                letterSpacing = (-0.5).sp,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = (if (isAmbientDark) Color.Black else Color.White).copy(alpha = 0.5f),
                        offset = Offset(0f, 1f),
                        blurRadius = 4f
                    )
                )
            )
            
            Text(
                text = "BRIGHT",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAmbientDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 1.dp),
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = (if (isAmbientDark) Color.Black else Color.White).copy(alpha = 0.5f),
                        offset = Offset(0f, 1f),
                        blurRadius = 3f
                    )
                )
            )
        }
    }
}


