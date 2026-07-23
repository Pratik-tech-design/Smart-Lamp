package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AmbientSoundType
import com.example.audio.LocalInteractionFeedbackManager
import com.example.audio.clickableWithFeedback
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt
import com.example.viewmodel.ColorPreset
import com.example.viewmodel.LampViewModel
import com.example.viewmodel.ReadingPreset

data class SheetTheme(
    val isDark: Boolean,
    val sheetBg: Color,
    val cardBg: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primaryActive: Color,
    val isLightActive: Color
)

val LocalSheetTheme = staticCompositionLocalOf<SheetTheme> {
    SheetTheme(
        isDark = false,
        sheetBg = Color.White,
        cardBg = Color(0xFFF7F8FA),
        border = Color(0xFFE5E5E5),
        textPrimary = Color(0xFF111111),
        textSecondary = Color(0xFF6B7280),
        primaryActive = Color(0xFF111111),
        isLightActive = Color(0xFF111111)
    )
}

@Composable
private fun SliderWithFeedback(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    colors: androidx.compose.material3.SliderColors = androidx.compose.material3.SliderDefaults.colors(),
    stepsCount: Int = 30,
    isBrightness: Boolean = false
) {
    val feedbackManager = LocalInteractionFeedbackManager.current
    val view = LocalView.current
    var lastStep by remember(valueRange, stepsCount) { mutableStateOf(-1) }

    Slider(
        value = value,
        onValueChange = { newVal ->
            onValueChange(newVal)
            val normalized = if (valueRange.endInclusive != valueRange.start) {
                (newVal - valueRange.start) / (valueRange.endInclusive - valueRange.start)
            } else {
                0f
            }
            val step = (normalized * stepsCount).roundToInt()
            if (lastStep != step) {
                lastStep = step
                if (isBrightness) {
                    feedbackManager.playBrightnessTick(view, newVal)
                } else {
                    feedbackManager.playInteractionClick(view)
                }
            }
        },
        onValueChangeFinished = {
            if (isBrightness) {
                feedbackManager.stopBrightnessTicks()
            }
        },
        valueRange = valueRange,
        colors = colors,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetControls(
    viewModel: LampViewModel,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOn by viewModel.isOn.collectAsState()
    val feedbackManager = LocalInteractionFeedbackManager.current
    val view = LocalView.current
    val brightness by viewModel.brightness.collectAsState()
    val roomDarkness by viewModel.roomDarkness.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val isAdvancedOpen by viewModel.isAdvancedColorPickerOpen.collectAsState()

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var dragAmount by remember { mutableStateOf(0f) }

    val travelDistanceDp = 260.dp
    val isFocusMode by viewModel.isFocusMode.collectAsState()

    // Synchronize with parent expanded state
    var isSheetExpandedState by remember { mutableStateOf(isExpanded) }
    LaunchedEffect(isExpanded) {
        isSheetExpandedState = isExpanded
    }

    // When focus mode is exited, reset the sheet to collapsed state
    LaunchedEffect(isFocusMode) {
        if (!isFocusMode) {
            isSheetExpandedState = false
        }
    }

    val baseOffsetDp = if (isSheetExpandedState) 0.dp else travelDistanceDp
    val dragAmountDp = with(density) { dragAmount.toDp() }
    val currentOffsetDp = (baseOffsetDp.value + dragAmountDp.value).coerceIn(0f, 550f).dp

    // If dragged almost completely down during active drag, trigger dismissal immediately
    LaunchedEffect(currentOffsetDp.value) {
        if (currentOffsetDp.value > 520f) {
            viewModel.setFocusMode(true)
            dragAmount = 0f
        }
    }

    val animatedTranslationY by animateDpAsState(
        targetValue = currentOffsetDp,
        animationSpec = if (dragAmount != 0f) {
            androidx.compose.animation.core.snap()
        } else {
            androidx.compose.animation.core.spring(
                dampingRatio = 0.85f,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
            )
        },
        label = "sheet_translation_y"
    )

    val draggableState = rememberDraggableState { delta ->
        dragAmount += delta
    }

    val onDragStop: (Float) -> Unit = { velocity ->
        val dragDistanceDp = with(density) { dragAmount.toDp() }
        val targetOffset = baseOffsetDp.value + dragDistanceDp.value
        
        if (isSheetExpandedState) {
            // Dragging from Expanded state (0.dp)
            if (targetOffset > 400f || velocity > 1200f) {
                // Large swipe down -> fully dismiss to focus mode
                viewModel.setFocusMode(true)
                isSheetExpandedState = false
                if (isExpanded) {
                    onToggleExpand()
                }
            } else if (targetOffset > 100f || velocity > 400f) {
                // Moderate swipe down -> collapse
                isSheetExpandedState = false
                if (isExpanded) {
                    onToggleExpand()
                }
            } else {
                // Snap back to expanded
                isSheetExpandedState = true
                if (!isExpanded) {
                    onToggleExpand()
                }
            }
        } else {
            // Dragging from Collapsed state (260.dp)
            if (targetOffset < 130f || velocity < -400f) {
                // Swipe up -> expand
                isSheetExpandedState = true
                if (!isExpanded) {
                    onToggleExpand()
                }
            } else if (targetOffset > 380f || velocity > 400f) {
                // Swipe down -> fully dismiss to focus mode
                viewModel.setFocusMode(true)
            } else {
                // Snap back to collapsed
                isSheetExpandedState = false
                if (isExpanded) {
                    onToggleExpand()
                }
            }
        }
        dragAmount = 0f
    }

    // Dynamic monochrome themes adapting live to Room Darkness
    val isDark = roomDarkness >= 0.5f
    val sheetBgColor by animateColorAsState(targetValue = if (isDark) Color(0xFF141416) else Color(0xFFFFFFFF), animationSpec = tween(400), label = "sheet_bg")
    val cardBgColor by animateColorAsState(targetValue = if (isDark) Color(0xFF1E1E22) else Color(0xFFF7F8FA), animationSpec = tween(400), label = "card_bg")
    val borderColor by animateColorAsState(targetValue = if (isDark) Color(0xFF2C2C30) else Color(0xFFE5E5E5), animationSpec = tween(400), label = "border_color")
    val textPrimaryColor by animateColorAsState(targetValue = if (isDark) Color(0xFFF5F5F7) else Color(0xFF111111), animationSpec = tween(400), label = "text_primary")
    val textSecondaryColor by animateColorAsState(targetValue = if (isDark) Color(0xFF9EAFBF) else Color(0xFF6B7280), animationSpec = tween(400), label = "text_secondary")
    val primaryActiveColor by animateColorAsState(targetValue = if (isDark) Color(0xFFFFFFFF) else Color(0xFF111111), animationSpec = tween(400), label = "primary_active")
    val lightActiveColor = if (isDark) Color(0xFF1E1E22) else Color(0xFF111111)

    val theme = SheetTheme(
        isDark = isDark,
        sheetBg = sheetBgColor,
        cardBg = cardBgColor,
        border = borderColor,
        textPrimary = textPrimaryColor,
        textSecondary = textSecondaryColor,
        primaryActive = primaryActiveColor,
        isLightActive = lightActiveColor
    )

    CompositionLocalProvider(LocalSheetTheme provides theme) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(600.dp)
                .graphicsLayer {
                    translationY = with(density) { animatedTranslationY.toPx() }
                }
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    clip = false
                ),
            color = theme.sheetBg,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // Drag handle and expand indicator (fully draggable and comfortable touch target)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickableWithFeedback { onToggleExpand() }
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> onDragStop(velocity) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp, 4.dp)
                                .clip(CircleShape)
                                .background(theme.border)
                        )
                    }
                }

                // Sheet scrollable contents
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    // Section 1: Power & Brightness
                    PowerAndBrightnessSection(viewModel, isOn, brightness, roomDarkness)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Section 2: Quick Color Presets & "Explore More" button
                    QuickColorSection(viewModel, selectedColor, onToggleExpand, isExpanded)

                    // Sections below are only visible when EXPANDED
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Section 3: Reading / Mood Presets
                            ReadingPresetsSection(viewModel)

                            Spacer(modifier = Modifier.height(24.dp))

                            // Section 4: Advanced Color Customizer (Toggleable inside expanded sheet)
                            AdvancedColorPickerSection(viewModel, isAdvancedOpen)

                            Spacer(modifier = Modifier.height(24.dp))

                            // Section 5: Automatic Timer Card
                            TimerSection(viewModel)

                            Spacer(modifier = Modifier.height(24.dp))

                            // Section 6: Ambient Relaxing Sound Player
                            AmbientSoundSection(viewModel)

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PowerAndBrightnessSection(
    viewModel: LampViewModel,
    isOn: Boolean,
    brightness: Float,
    roomDarkness: Float
) {
    val theme = LocalSheetTheme.current
    val feedbackManager = LocalInteractionFeedbackManager.current
    val view = LocalView.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("power_brightness_card"),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Power row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isOn) theme.primaryActive.copy(alpha = 0.12f) else theme.border),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "Power Icon",
                            tint = if (isOn) theme.primaryActive else theme.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AfterDark",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = if (isOn) "Active illumination" else "Standby Mode",
                            fontSize = 12.sp,
                            color = theme.textSecondary
                        )
                    }
                }

                Switch(
                    checked = isOn,
                    onCheckedChange = {
                        viewModel.togglePower()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (theme.isDark) Color.Black else Color.White,
                        checkedTrackColor = theme.primaryActive,
                        uncheckedThumbColor = theme.textSecondary,
                        uncheckedTrackColor = theme.border
                    ),
                    modifier = Modifier.testTag("power_switch")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Brightness Control Row with dynamic percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lamp Brightness",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary
                )
                Text(
                    text = "${(brightness * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.primaryActive
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tactile slider
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.LightMode,
                    contentDescription = "Low Brightness",
                    tint = theme.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SliderWithFeedback(
                    value = brightness,
                    onValueChange = { viewModel.setBrightness(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.primaryActive,
                        activeTrackColor = theme.primaryActive,
                        inactiveTrackColor = theme.border
                    ),
                    isBrightness = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("brightness_slider")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = "High Brightness",
                    tint = theme.primaryActive,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // System Brightness Sync Options Card
            val systemBrightnessSync by viewModel.systemBrightnessSync.collectAsState()
            val hasWriteSettingsPermission by viewModel.hasWriteSettingsPermission.collectAsState()
            val context = LocalContext.current

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = theme.cardBg.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.border.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableWithFeedback {
                        if (!hasWriteSettingsPermission) {
                            viewModel.setShowPermissionDialog(true)
                        }
                    }
                    .testTag("system_brightness_sync_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(theme.primaryActive.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings icon",
                                tint = theme.primaryActive,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Device Brightness Sync",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = if (hasWriteSettingsPermission) "System-wide sync active" else "Window-only active (Tap to upgrade)",
                                fontSize = 11.sp,
                                color = if (hasWriteSettingsPermission) theme.primaryActive else theme.textSecondary
                            )
                        }
                    }
                    
                    Switch(
                        checked = systemBrightnessSync,
                        onCheckedChange = { isChecked ->
                            feedbackManager.playInteractionClick(view)
                            if (isChecked) {
                                val checkContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    context.createAttributionContext("default")
                                } else {
                                    context
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                                    !android.provider.Settings.System.canWrite(checkContext)) {
                                    viewModel.setShowPermissionDialog(true)
                                } else {
                                    viewModel.setSystemBrightnessSync(true)
                                }
                            } else {
                                viewModel.setSystemBrightnessSync(false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = if (theme.isDark) Color.Black else Color.White,
                            checkedTrackColor = theme.primaryActive,
                            uncheckedThumbColor = theme.textSecondary,
                            uncheckedTrackColor = theme.border
                        ),
                        modifier = Modifier.testTag("system_brightness_sync_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Room Darkness Control Row with dynamic percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Room Darkness",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary
                )
                Text(
                    text = "${(roomDarkness * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.primaryActive
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tactile slider for room darkness
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌑",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                SliderWithFeedback(
                    value = roomDarkness,
                    onValueChange = { viewModel.setRoomDarkness(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.primaryActive,
                        activeTrackColor = theme.primaryActive,
                        inactiveTrackColor = theme.border
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("room_darkness_slider")
                )
                Text(
                    text = "🌕",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun QuickColorSection(
    viewModel: LampViewModel,
    selectedColor: Color,
    onToggleExpand: () -> Unit,
    isExpanded: Boolean
) {
    val theme = LocalSheetTheme.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ambient White Presets",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickableWithFeedback {
                    if (!isExpanded) {
                        onToggleExpand()
                    }
                    viewModel.setAdvancedColorPickerOpen(!viewModel.isAdvancedColorPickerOpen.value)
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = "Color wheel icon",
                    tint = theme.primaryActive,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Explore Colors",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.primaryActive
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal scrolling color chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(viewModel.colorPresets) { preset ->
                val isSelected = selectedColor == preset.color
                val chipBgColor = if (isSelected) theme.primaryActive.copy(alpha = 0.12f) else theme.cardBg

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipBgColor)
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) theme.primaryActive else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickableWithFeedback { viewModel.selectColorPreset(preset) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(preset.color)
                            .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = preset.name,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) theme.primaryActive else theme.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ReadingPresetsSection(
    viewModel: LampViewModel
) {
    val theme = LocalSheetTheme.current
    val activePresetName by viewModel.activePresetName.collectAsState()

    Column {
        Text(
            text = "Reading & Mood Modes",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = theme.textPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(viewModel.readingPresets) { preset ->
                val isSelected = activePresetName == preset.name
                val chipBg = if (isSelected) theme.primaryActive else theme.cardBg
                val textColor = if (isSelected) (if (theme.isDark) Color.Black else Color.White) else theme.textPrimary
                val iconTint = if (isSelected) (if (theme.isDark) Color.Black else Color.White) else theme.textSecondary

                // Select icon representation
                val icon = when (preset.name) {
                    "Reading" -> Icons.Rounded.Book
                    "Study" -> Icons.Rounded.School
                    "Focus" -> Icons.Rounded.CenterFocusStrong
                    "Relax" -> Icons.Rounded.Spa
                    "Night" -> Icons.Rounded.Bedtime
                    "Sleep" -> Icons.Rounded.NightsStay
                    "Meditation" -> Icons.Rounded.SelfImprovement
                    else -> Icons.Rounded.LightMode
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .clickableWithFeedback { viewModel.selectReadingPreset(preset) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = preset.name,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = preset.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedColorPickerSection(
    viewModel: LampViewModel,
    isOpen: Boolean
) {
    if (!isOpen) return

    val theme = LocalSheetTheme.current
    val feedbackManager = LocalInteractionFeedbackManager.current
    val view = LocalView.current
    val selectedColor by viewModel.selectedColor.collectAsState()
    val recentColors by viewModel.recentColors.collectAsState()
    val favoriteColors by viewModel.favoriteColors.collectAsState()
    val colorTempK by viewModel.colorTempK.collectAsState()

    // Extract HSL/RGB properties for sliders
    val hsv = remember(selectedColor) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColor.toArgb(), arr)
        arr
    }

    val focusManager = LocalFocusManager.current
    var hexInputText by remember(selectedColor) {
        mutableStateOf(String.format("#%06X", 0xFFFFFF and selectedColor.toArgb()))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Advanced Color Customizer",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Hue Slider (Rainbow background)
            Text(
                text = "Hue Spectrum",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Red, Color.Yellow, Color.Green,
                                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                            )
                        )
                    )
            )
            SliderWithFeedback(
                value = hsv[0],
                onValueChange = { h ->
                    hsv[0] = h
                    val argb = android.graphics.Color.HSVToColor(hsv)
                    viewModel.selectCustomColor(Color(argb))
                },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.primaryActive,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.height(28.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Saturation Slider
            Text(
                text = "Saturation",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            val baseHueColor = remember(hsv[0]) {
                val baseHsv = floatArrayOf(hsv[0], 1f, 1f)
                Color(android.graphics.Color.HSVToColor(baseHsv))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.White, baseHueColor)
                        )
                    )
            )
            SliderWithFeedback(
                value = hsv[1],
                onValueChange = { s ->
                    hsv[1] = s
                    val argb = android.graphics.Color.HSVToColor(hsv)
                    viewModel.selectCustomColor(Color(argb))
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.primaryActive,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.height(28.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Brightness/Value Slider
            Text(
                text = "Value (Intensity)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.Black, baseHueColor)
                        )
                    )
            )
            SliderWithFeedback(
                value = hsv[2],
                onValueChange = { v ->
                    hsv[2] = v
                    val argb = android.graphics.Color.HSVToColor(hsv)
                    viewModel.selectCustomColor(Color(argb))
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.primaryActive,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.height(28.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Color Temperature Kelvin Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Color Temp: $colorTempK K",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textSecondary
                )
                val tempLabel = when {
                    colorTempK < 2500 -> "Candlelight"
                    colorTempK < 3500 -> "Warm Relax"
                    colorTempK < 4500 -> "Halogen"
                    colorTempK < 5500 -> "Natural daylight"
                    else -> "Crisp Cool"
                }
                Text(
                    text = tempLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.primaryActive
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFF8A00), Color(0xFFFFE8B0), Color(0xFFD4E3FF), Color(0xFF99C2FF))
                        )
                    )
            )
            SliderWithFeedback(
                value = colorTempK.toFloat(),
                onValueChange = { temp ->
                    viewModel.selectColorTempK(temp.toInt())
                },
                valueRange = 1500f..8000f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.primaryActive,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.height(28.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // HEX Manual input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(selectedColor)
                            .border(1.5.dp, theme.textSecondary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = hexInputText,
                        onValueChange = { input ->
                            if (input.length <= 7) {
                                hexInputText = input
                                if (input.startsWith("#") && input.length == 7) {
                                    try {
                                        val parsed = android.graphics.Color.parseColor(input)
                                        viewModel.selectCustomColor(Color(parsed))
                                        focusManager.clearFocus()
                                    } catch (e: Exception) {
                                        // Silent parsing error
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedBorderColor = theme.primaryActive,
                            unfocusedBorderColor = theme.border
                        ),
                        modifier = Modifier
                            .widthIn(max = 100.dp)
                            .height(48.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }

                // Dynamic Favorite Toggle Action Button
                val isFav = favoriteColors.contains(selectedColor)
                IconButton(
                    onClick = {
                        feedbackManager.playInteractionClick(view)
                        viewModel.toggleFavoriteColor(selectedColor)
                    }
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Save favorite",
                        tint = if (isFav) Color(0xFFFF4B4B) else theme.textSecondary
                    )
                }
            }

            // Recent and Favorite chips Row
            if (recentColors.isNotEmpty() || favoriteColors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recent Swatches",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentColors.take(6)) { col ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (col == selectedColor) 2.dp else 1.dp,
                                    color = if (col == selectedColor) theme.primaryActive else Color.Black.copy(alpha = 0.15f),
                                    shape = CircleShape
                                )
                                .clickableWithFeedback { viewModel.selectCustomColor(col) }
                        )
                    }
                }

                if (favoriteColors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Saved Favorites",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(favoriteColors) { col ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (col == selectedColor) 2.dp else 1.dp,
                                        color = if (col == selectedColor) theme.primaryActive else Color.Black.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                                    .clickableWithFeedback { viewModel.selectCustomColor(col) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimerSection(
    viewModel: LampViewModel
) {
    val theme = LocalSheetTheme.current
    val secondsLeft by viewModel.timerSecondsLeft.collectAsState()
    val totalSeconds by viewModel.timerDurationSeconds.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header timer title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = "Timer icon",
                        tint = theme.primaryActive,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sleep Timer",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }

                if (secondsLeft > 0) {
                    val mins = secondsLeft / 60
                    val secs = secondsLeft % 60
                    val formattedTime = String.format("%02d:%02d", mins, secs)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFB020).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = formattedTime,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB020)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cancel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textSecondary,
                            modifier = Modifier.clickableWithFeedback { viewModel.cancelTimer() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Timer Presets layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val timers = listOf(
                    5 to "5m",
                    10 to "10m",
                    15 to "15m",
                    30 to "30m",
                    45 to "45min",
                    60 to "1h",
                    90 to "1:30 min",
                    120 to "2h"
                )

                timers.forEach { (mins, label) ->
                    val isActive = secondsLeft > 0 && (totalSeconds == mins * 60L)
                    val buttonBg = if (isActive) theme.primaryActive else theme.sheetBg
                    val borderCol = if (isActive) theme.primaryActive else theme.border
                    val labelCol = if (isActive) (if (theme.isDark) Color.Black else Color.White) else theme.textPrimary

                    Box(
                        modifier = Modifier
                            .widthIn(min = 68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(buttonBg)
                            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                            .clickableWithFeedback { viewModel.startTimer(mins) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelCol
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AmbientSoundSection(
    viewModel: LampViewModel
) {
    val theme = LocalSheetTheme.current
    val feedbackManager = LocalInteractionFeedbackManager.current
    val view = LocalView.current
    val activeSound by viewModel.ambientSound.collectAsState()
    val isPlaying by viewModel.isPlayingSound.collectAsState()
    val volume by viewModel.ambientVolume.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Sound Title & Play/Pause Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Soundscapes",
                        tint = theme.primaryActive,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ambient Soundscapes",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }

                if (activeSound != AmbientSoundType.NONE) {
                    IconButton(
                        onClick = {
                            feedbackManager.playInteractionClick(view)
                            viewModel.togglePlaySound()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(theme.primaryActive)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play sound trigger",
                            tint = if (theme.isDark) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sound scrolling layout
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(AmbientSoundType.values()) { sound ->
                    val isSelected = activeSound == sound
                    val chipBg = if (isSelected) theme.primaryActive.copy(alpha = 0.12f) else theme.sheetBg
                    val borderCol = if (isSelected) theme.primaryActive else theme.border
                    val textCol = if (isSelected) theme.primaryActive else theme.textPrimary

                    if (sound != AmbientSoundType.NONE) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(chipBg)
                                .border(1.2.dp, borderCol, RoundedCornerShape(12.dp))
                                .clickableWithFeedback { viewModel.selectAmbientSound(sound) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected && isPlaying) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "Playing",
                                    tint = theme.primaryActive,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = sound.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }
                    }
                }
            }

            // Sound volume control (Only visible if sound is active)
            if (activeSound != AmbientSoundType.NONE) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = "Sound volume",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    SliderWithFeedback(
                        value = volume,
                        onValueChange = { viewModel.setAmbientVolume(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.primaryActive,
                            activeTrackColor = theme.primaryActive,
                            inactiveTrackColor = theme.border
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
