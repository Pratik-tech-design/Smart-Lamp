package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.LocalInteractionFeedbackManager
import com.example.viewmodel.LampViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun HangingLamp(
    viewModel: LampViewModel,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isOn by viewModel.isOn.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val lampScale by viewModel.lampScale.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val feedbackManager = LocalInteractionFeedbackManager.current

    // Sizing and layout measurements
    var containerWidth by remember { mutableStateOf(0f) }
    var containerHeight by remember { mutableStateOf(0f) }

    val density = LocalDensity.current
    val isFocusMode by viewModel.isFocusMode.collectAsState()

    val baseCableLength = remember(density) { with(density) { 140.dp.toPx() } }
    val nominalCableLength by animateFloatAsState(
        targetValue = if (isFocusMode && containerHeight > 0f) {
            containerHeight * 0.45f
        } else {
            baseCableLength
        },
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessLow
        ),
        label = "nominal_cable_length"
    )

    val focusScaleFactor by animateFloatAsState(
        targetValue = if (isFocusMode) 1.45f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "focus_scale"
    )

    // Pull cord offset and smooth glow factor
    val pullOffset = remember { Animatable(0f) }
    val glowFactor by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "glow_factor"
    )

    // Physical simulation states replaced with premium static cable and rigid body animatable
    val angle = remember { Animatable(0f) } // theta in radians
    var isDragging by remember { mutableStateOf(false) }
    var targetAngle by remember { mutableStateOf(0f) }

    // Tap scale bump microinteraction
    var tapScaleBump by remember { mutableStateOf(1f) }
    val animatedTapScale by animateFloatAsState(
        targetValue = tapScaleBump,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = {
            if (tapScaleBump > 1f) {
                tapScaleBump = 1f
            }
        }
    )

    val length = nominalCableLength
    var lastTapTime by remember { mutableStateOf(0L) }

    // Sync bulb position with ViewModel whenever the interactive angle or length changes
    LaunchedEffect(angle.value, length, containerWidth, lampScale, focusScaleFactor, pullOffset.value) {
        if (containerWidth > 0f && length > 0f) {
            val scale = lampScale * focusScaleFactor
            val centerX = containerWidth / 2f
            val holderHeightPx = with(density) { 44.dp.toPx() } * scale
            val spacerHeightPx = with(density) { 4.dp.toPx() } * scale
            val bulbRadiusPx = with(density) { 32.dp.toPx() } * scale
            val lampBodyLength = holderHeightPx + spacerHeightPx + bulbRadiusPx * 0.8f

            val bX = centerX + lampBodyLength * sin(angle.value)
            val bY = length + pullOffset.value + lampBodyLength * cos(angle.value)
            viewModel.updateBulbPosition(androidx.compose.ui.geometry.Offset(bX, bY))
        }
    }

    // Pinch-to-zoom state for resizing (from 0.8x to 2.5x as required)
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        viewModel.setLampScale((lampScale * zoomChange).coerceIn(0.8f, 2.5f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFocusMode) Modifier.fillMaxSize() else Modifier.height(340.dp))
            .testTag("hanging_lamp_container")
            .onSizeChanged { size ->
                containerWidth = size.width.toFloat()
                containerHeight = size.height.toFloat()
            }
            .transformable(state = transformState)
            .pointerInput(containerWidth, length, isOn, brightness, lampScale, focusScaleFactor) {
                if (containerWidth == 0f) return@pointerInput

                val scale = lampScale * focusScaleFactor
                val holderHeightPx = 44.dp.toPx() * scale
                val spacerHeightPx = 4.dp.toPx() * scale
                val bulbRadiusPx = 32.dp.toPx() * scale
                val lampBodyLength = holderHeightPx + spacerHeightPx + bulbRadiusPx * 0.8f
                val centerX = containerWidth / 2f
                val bulbCenterY = length + lampBodyLength

                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        // Determine if touch is targeting the bulb assembly
                        val dx = down.position.x - centerX
                        val dy = down.position.y - bulbCenterY
                        val distToBulb = sqrt(dx * dx + dy * dy)

                        // Target click bounds is set to 85.dp for ergonomics
                        if (distToBulb > 85.dp.toPx() * scale) {
                            continue
                        }

                        // Touch initiated! Prepare state tracking
                        var isLongPress = false
                        val currentPointerId = down.id
                        var currentPosition = down.position
                        val startTime = System.currentTimeMillis()

                        val longPressTimeout = 300L // Tactile responsiveness timing

                        val longPressCompleted = withTimeoutOrNull(longPressTimeout) {
                            var pointerDown = true
                            while (pointerDown) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == currentPointerId }
                                if (change == null || !change.pressed) {
                                    pointerDown = false
                                } else {
                                    currentPosition = change.position
                                    val dist = sqrt((currentPosition.x - down.position.x).pow(2) + (currentPosition.y - down.position.y).pow(2))
                                    if (dist > 15.dp.toPx()) {
                                        pointerDown = false // User swiped/dragged, cancel long press
                                    }
                                }
                            }
                            false
                        }

                        isLongPress = (longPressCompleted == null)

                        if (isLongPress) {
                            // ENTERED PULL-CORD MODE!
                            feedbackManager.playContinuousTick(view, isEdge = true)
                            isDragging = true
                            var pullDistance = 0f
                            val maxPullDistance = 65.dp.toPx() * scale

                            var pointerDown = true
                            var lastPullStep = -1
                            while (pointerDown) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == currentPointerId }
                                if (change == null || !change.pressed) {
                                    pointerDown = false
                                } else {
                                    change.consume()
                                    // Move only downward (from rest point)
                                    val deltaY = change.position.y - down.position.y
                                    pullDistance = deltaY.coerceIn(0f, maxPullDistance)

                                    val pullStep = (pullDistance / (5.dp.toPx() * scale)).toInt()
                                    if (pullStep != lastPullStep) {
                                        lastPullStep = pullStep
                                        feedbackManager.playContinuousTick(view, isEdge = false)
                                    }

                                    coroutineScope.launch {
                                        pullOffset.snapTo(pullDistance)
                                    }
                                }
                            }

                            isDragging = false

                            // Mechanical click snap power toggle check
                            val threshold = 45.dp.toPx() * scale
                            if (pullDistance >= threshold) {
                                viewModel.togglePower()
                            } else {
                                feedbackManager.playContinuousTick(view, isEdge = false)
                            }

                            // Smooth spring back
                            coroutineScope.launch {
                                pullOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        } else {
                            // TAP OR STANDARD SWIPE GESTURE!
                            val elapsed = System.currentTimeMillis() - startTime
                            val finalDist = sqrt((currentPosition.x - down.position.x).pow(2) + (currentPosition.y - down.position.y).pow(2))

                            if (elapsed < 300L && finalDist < 12.dp.toPx()) {
                                // TAP DETECTED!
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 300L) {
                                    // DOUBLE TAP: Toggle Focus Mode
                                    feedbackManager.playInteractionClick(view)
                                    viewModel.toggleFocusMode()
                                    lastTapTime = 0L
                                } else {
                                    // SINGLE TAP: Toggle bottom sheet controls
                                    lastTapTime = now
                                    feedbackManager.playInteractionClick(view)
                                    tapScaleBump = 1.15f
                                    onTap()
                                }
                            } else {
                                // DRAGGING / SWIPING: Adjust swing angle or brightness
                                isDragging = true
                                var lastX = currentPosition.x
                                var lastY = currentPosition.y
                                val startBrightness = brightness
                                var isVerticalSwipe = false
                                var accumulatedDragY = 0f
                                var accumulatedDragX = 0f

                                targetAngle = angle.value

                                var dragDown = true
                                while (dragDown) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == currentPointerId }
                                    if (change == null || !change.pressed) {
                                        dragDown = false
                                    } else {
                                        change.consume()

                                        val currentX = change.position.x
                                        val currentY = change.position.y

                                        val deltaX = currentX - lastX
                                        val deltaY = currentY - lastY

                                        accumulatedDragX += deltaX
                                        accumulatedDragY += deltaY

                                        val dxLocal = currentX - centerX
                                        val dyLocal = currentY - length

                                        // Vertical swipe for brightness disabled to prevent interference
                                        if (false) {
                                            isVerticalSwipe = true
                                            viewModel.setAdjustingBrightness(true)
                                        }

                                        if (isVerticalSwipe) {
                                            val sensitivity = 0.003f
                                            val newBrightness = (startBrightness - accumulatedDragY * sensitivity).coerceIn(0.01f, 1.0f)
                                            viewModel.setBrightness(newBrightness)
                                            targetAngle = 0f
                                        } else {
                                            val computed = computedAngle(dxLocal, dyLocal)
                                            val maxAngleRad = 55f * (PI.toFloat() / 180f)
                                            targetAngle = computed.coerceIn(-maxAngleRad, maxAngleRad)
                                        }

                                        coroutineScope.launch {
                                            angle.snapTo(targetAngle)
                                        }

                                        lastX = currentX
                                        lastY = currentY
                                    }
                                }

                                if (isVerticalSwipe) {
                                    viewModel.setAdjustingBrightness(false)
                                }
                                isDragging = false

                                // Smooth rigid return to center
                                coroutineScope.launch {
                                    angle.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val startPoint = Offset(width / 2f, 0f)

            val currentScale = lampScale * animatedTapScale * focusScaleFactor
            val bulbRadius = 32.dp.toPx() * currentScale
            val holderWidth = 36.dp.toPx() * currentScale
            val holderHeight = 44.dp.toPx() * currentScale
            val holderLeft = startPoint.x - holderWidth / 2f
            val holderTop = length + pullOffset.value
            val spacerHeight = 4.dp.toPx() * currentScale
            val sphereCenter = Offset(startPoint.x, holderTop + holderHeight + spacerHeight + bulbRadius * 0.8f)

            val currentGlow = glowFactor

            // Dynamic 3D Wall Shadow perspective shift lagging behind the lamp
            val shadowOffset = Offset(
                x = -sin(angle.value) * 16f,
                y = (1f - cos(angle.value)) * 8f + 12f
            )

            // Dynamic shadow behavior: softens (larger, lighter) with higher brightness, darker when OFF
            val shadowAlpha = if (currentGlow > 0.01f) {
                ((0.12f * (1f - brightness) + 0.03f).coerceIn(0.01f, 0.15f) * currentGlow + 0.16f * (1f - currentGlow))
            } else {
                0.16f
            }
            val shadowColor = Color(0xFF040508).copy(alpha = shadowAlpha)
            val shadowBlurScale = (1f + brightness * 0.5f) * currentGlow + 1.0f * (1f - currentGlow)

            // ================= 1. DRAW SHADOWS =================
            // Cable Shadow (drawn statically)
            val shadowPath = Path().apply {
                moveTo(startPoint.x, startPoint.y)
                lineTo(startPoint.x, length)
            }
            drawPath(
                path = shadowPath,
                color = shadowColor.copy(alpha = shadowColor.alpha * 0.4f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.5.dp.toPx() * shadowBlurScale,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )

            // Holder, Spacer, Bulb Shadow (rotated around Offset(startPoint.x, length + pullOffset.value))
            withTransform({
                translate(left = shadowOffset.x, top = shadowOffset.y)
                rotate(degrees = Math.toDegrees(angle.value.toDouble()).toFloat(), pivot = Offset(startPoint.x, length + pullOffset.value))
                val stretchY = 1.0f + kotlin.math.abs(sin(angle.value)) * 0.12f
                scale(scaleX = 1.0f, scaleY = stretchY, pivot = Offset(startPoint.x, length + pullOffset.value))
            }) {
                // Holder and Spacer Shadow
                drawRoundRect(
                    color = shadowColor,
                    topLeft = Offset(
                        holderLeft - (shadowBlurScale - 1f) * 3f,
                        holderTop - (shadowBlurScale - 1f) * 3f
                    ),
                    size = Size(
                        holderWidth + (shadowBlurScale - 1f) * 6f,
                        holderHeight + (shadowBlurScale - 1f) * 6f
                    ),
                    cornerRadius = CornerRadius(8.dp.toPx() * currentScale * shadowBlurScale, 8.dp.toPx() * currentScale * shadowBlurScale)
                )

                // Bulb Shadow
                drawCircle(
                    color = shadowColor,
                    center = sphereCenter,
                    radius = bulbRadius * shadowBlurScale
                )
            }

            // ================= 2. THE PHYSICAL HANGING CABLE (Perfecty straight, completely motionless) =================
            val cablePath = Path().apply {
                moveTo(startPoint.x, startPoint.y)
                lineTo(startPoint.x, length)
            }

            val cableBrush = if (currentGlow > 0.01f) {
                val r = (0.28f * (1f - brightness) + selectedColor.red * brightness).coerceIn(0f, 1f)
                val g = (0.30f * (1f - brightness) + selectedColor.green * brightness).coerceIn(0f, 1f)
                val b = (0.35f * (1f - brightness) + selectedColor.blue * brightness).coerceIn(0f, 1f)
                
                val cableOnColor = Color(red = r, green = g, blue = b, alpha = 0.85f * currentGlow + 0.1f)
                val cableOffColor = Color(0xFF161820)
                
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111218),
                        Color(
                            red = cableOnColor.red * currentGlow + cableOffColor.red * (1f - currentGlow),
                            green = cableOnColor.green * currentGlow + cableOffColor.green * (1f - currentGlow),
                            blue = cableOnColor.blue * currentGlow + cableOffColor.blue * (1f - currentGlow),
                            alpha = cableOnColor.alpha * currentGlow + cableOffColor.alpha * (1f - currentGlow)
                        )
                    ),
                    startY = startPoint.y,
                    endY = length
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF111218), Color(0xFF161820)),
                    startY = startPoint.y,
                    endY = length
                )
            }

            drawPath(
                path = cablePath,
                brush = cableBrush,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )

            // ================= 2B. BEADED METALLIC PULL CHAIN (Becomes visible when user pulls the lamp) =================
            if (pullOffset.value > 0.5f) {
                val beadRadius = 2.dp.toPx() * currentScale
                val beadSpacing = 5.dp.toPx() * currentScale
                val numBeads = (pullOffset.value / beadSpacing).toInt().coerceAtLeast(1)
                for (i in 0 until numBeads) {
                    val y = length + (i + 0.5f) * (pullOffset.value / numBeads)
                    drawCircle(
                        color = if (currentGlow > 0.5f) selectedColor.copy(alpha = 0.8f) else Color(0xFF8E9297),
                        center = Offset(startPoint.x, y),
                        radius = beadRadius
                    )
                }
            }

            // ================= 3. DRAW MAIN RIGID BODY ASSEMBLY (Rotates around the bottom end of the cable) =================
            withTransform({
                rotate(degrees = Math.toDegrees(angle.value.toDouble()).toFloat(), pivot = Offset(startPoint.x, length + pullOffset.value))
            }) {
                // ================= 3A. THE PHYSICAL LIGHTING BLOOM LAYERS =================
                if (currentGlow > 0.01f) {
                    // Layer A: Very Large Screen-Wide Ambient Halo
                    val haloRadius = 320.dp.toPx() * currentScale * (0.4f + 0.6f * brightness)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                selectedColor.copy(alpha = 0.22f * brightness * currentGlow),
                                selectedColor.copy(alpha = 0.10f * brightness * currentGlow),
                                selectedColor.copy(alpha = 0.03f * brightness * currentGlow),
                                Color.Transparent
                            ),
                            center = sphereCenter,
                            radius = haloRadius
                        ),
                        center = sphereCenter,
                        radius = haloRadius
                    )

                    // Layer B: Soft Outer Bloom Glow
                    val outerBloomRadius = 100.dp.toPx() * currentScale * (0.5f + 0.5f * brightness)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                selectedColor.copy(alpha = 0.48f * brightness * currentGlow),
                                selectedColor.copy(alpha = 0.22f * brightness * currentGlow),
                                selectedColor.copy(alpha = 0.05f * brightness * currentGlow),
                                Color.Transparent
                            ),
                            center = sphereCenter,
                            radius = outerBloomRadius
                        ),
                        center = sphereCenter,
                        radius = outerBloomRadius
                    )

                    // Layer C: Intense Glass Globe Glow
                    val glassGlowRadius = 52.dp.toPx() * currentScale * (0.7f + 0.3f * brightness)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f * brightness * currentGlow),
                                selectedColor.copy(alpha = 0.85f * brightness * currentGlow),
                                selectedColor.copy(alpha = 0.35f * brightness * currentGlow),
                                Color.Transparent
                            ),
                            center = sphereCenter,
                            radius = glassGlowRadius
                        ),
                        center = sphereCenter,
                        radius = glassGlowRadius
                    )
                }

                // ================= 3B. THE PREMIUM WOODEN HOUSING =================
                val woodLeftOn = Color(
                    red = (0.30f + 0.35f * selectedColor.red * brightness).coerceIn(0f, 1f),
                    green = (0.17f + 0.35f * selectedColor.green * brightness).coerceIn(0f, 1f),
                    blue = (0.10f + 0.35f * selectedColor.blue * brightness).coerceIn(0f, 1f)
                )
                val woodLeftOff = Color(0xFF382013)
                val woodLeft = Color(
                    red = woodLeftOn.red * currentGlow + woodLeftOff.red * (1f - currentGlow),
                    green = woodLeftOn.green * currentGlow + woodLeftOff.green * (1f - currentGlow),
                    blue = woodLeftOn.blue * currentGlow + woodLeftOff.blue * (1f - currentGlow)
                )

                val woodRightOn = Color(
                    red = (0.18f + 0.15f * selectedColor.red * brightness).coerceIn(0f, 1f),
                    green = (0.10f + 0.15f * selectedColor.green * brightness).coerceIn(0f, 1f),
                    blue = (0.06f + 0.15f * selectedColor.blue * brightness).coerceIn(0f, 1f)
                )
                val woodRightOff = Color(0xFF1F1009)
                val woodRight = Color(
                    red = woodRightOn.red * currentGlow + woodRightOff.red * (1f - currentGlow),
                    green = woodRightOn.green * currentGlow + woodRightOff.green * (1f - currentGlow),
                    blue = woodRightOn.blue * currentGlow + woodRightOff.blue * (1f - currentGlow)
                )

                val woodBrush = Brush.horizontalGradient(
                    colors = listOf(woodLeft, woodRight),
                    startX = holderLeft,
                    endX = holderLeft + holderWidth
                )

                drawRoundRect(
                    brush = woodBrush,
                    topLeft = Offset(holderLeft, holderTop),
                    size = Size(holderWidth, holderHeight),
                    cornerRadius = CornerRadius(8.dp.toPx() * currentScale, 8.dp.toPx() * currentScale)
                )

                // Wood grain curves
                val grainAlpha = (0.12f + 0.28f * brightness) * currentGlow + 0.10f * (1f - currentGlow)
                val grainColor = Color(0xFF120602).copy(alpha = grainAlpha)

                val grainPath1 = Path().apply {
                    moveTo(holderLeft + holderWidth * 0.28f, holderTop)
                    cubicTo(
                        holderLeft + holderWidth * 0.22f, holderTop + holderHeight * 0.3f,
                        holderLeft + holderWidth * 0.36f, holderTop + holderHeight * 0.7f,
                        holderLeft + holderWidth * 0.31f, holderTop + holderHeight
                    )
                }
                val grainPath2 = Path().apply {
                    moveTo(holderLeft + holderWidth * 0.54f, holderTop)
                    cubicTo(
                        holderLeft + holderWidth * 0.59f, holderTop + holderHeight * 0.3f,
                        holderLeft + holderWidth * 0.44f, holderTop + holderHeight * 0.7f,
                        holderLeft + holderWidth * 0.49f, holderTop + holderHeight
                    )
                }
                val grainPath3 = Path().apply {
                    moveTo(holderLeft + holderWidth * 0.78f, holderTop)
                    cubicTo(
                        holderLeft + holderWidth * 0.73f, holderTop + holderHeight * 0.3f,
                        holderLeft + holderWidth * 0.83f, holderTop + holderHeight * 0.7f,
                        holderLeft + holderWidth * 0.77f, holderTop + holderHeight
                    )
                }

                drawPath(path = grainPath1, color = grainColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                drawPath(path = grainPath2, color = grainColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()))
                drawPath(path = grainPath3, color = grainColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))

                // Left highlight reflection on wood
                val highlightAlpha = (0.15f + 0.20f * brightness) * currentGlow + 0.08f * (1f - currentGlow)
                drawRoundRect(
                    color = Color.White.copy(alpha = highlightAlpha),
                    topLeft = Offset(holderLeft + 2.dp.toPx(), holderTop + 2.dp.toPx()),
                    size = Size(holderWidth * 0.15f, holderHeight - 4.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx() * currentScale, 4.dp.toPx() * currentScale)
                )

                // Dark matte spacer
                val spacerWidth = holderWidth * 0.6f
                drawRect(
                    color = Color(0xFF15161A),
                    topLeft = Offset(startPoint.x - spacerWidth / 2f, holderTop + holderHeight),
                    size = Size(spacerWidth, spacerHeight)
                )

                // ================= 3C. THE FROSTED GLASS GLOBE BULB =================
                val glassBrush = if (currentGlow > 0.01f) {
                    val r1 = 1.0f * currentGlow + 0.83f * (1f - currentGlow)
                    val g1 = 1.0f * currentGlow + 0.85f * (1f - currentGlow)
                    val b1 = 1.0f * currentGlow + 0.90f * (1f - currentGlow)

                    val r2 = selectedColor.red * currentGlow + 0.62f * (1f - currentGlow)
                    val g2 = selectedColor.green * currentGlow + 0.69f * (1f - currentGlow)
                    val b2 = selectedColor.blue * currentGlow + 0.75f * (1f - currentGlow)

                    val r3 = selectedColor.red * currentGlow + 0.39f * (1f - currentGlow)
                    val g3 = selectedColor.green * currentGlow + 0.45f * (1f - currentGlow)
                    val b3 = selectedColor.blue * currentGlow + 0.52f * (1f - currentGlow)

                    Brush.radialGradient(
                        colors = listOf(
                            Color(red = r1, green = g1, blue = b1).copy(alpha = 1f),
                            Color(red = r2, green = g2, blue = b2).copy(alpha = 0.92f * currentGlow + 1.0f * (1f - currentGlow)),
                            Color(red = r3, green = g3, blue = b3).copy(alpha = 0.72f * currentGlow + 1.0f * (1f - currentGlow))
                        ),
                        center = sphereCenter - Offset(bulbRadius * 0.15f, bulbRadius * 0.15f),
                        radius = bulbRadius
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD4D8E6),
                            Color(0xFF9EAFBF),
                            Color(0xFF637385)
                        ),
                        center = sphereCenter - Offset(bulbRadius * 0.15f, bulbRadius * 0.15f),
                        radius = bulbRadius
                    )
                }

                drawCircle(
                    brush = glassBrush,
                    center = sphereCenter,
                    radius = bulbRadius
                )

                // Inner bright LED core filament bloom
                if (currentGlow > 0.01f) {
                    val coreRadius = 14.dp.toPx() * currentScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = currentGlow),
                                Color.White.copy(alpha = 0.90f * currentGlow),
                                selectedColor.copy(alpha = 0.40f * currentGlow),
                                Color.Transparent
                            ),
                            center = sphereCenter,
                            radius = coreRadius
                        ),
                        center = sphereCenter,
                        radius = coreRadius
                    )
                }

                // Elegant outer glass envelope line
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f).copy(
                        red = selectedColor.red * currentGlow + 1.0f * (1f - currentGlow),
                        green = selectedColor.green * currentGlow + 1.0f * (1f - currentGlow),
                        blue = selectedColor.blue * currentGlow + 1.0f * (1f - currentGlow),
                        alpha = 0.35f * currentGlow + 0.25f * (1f - currentGlow)
                    ),
                    center = sphereCenter,
                    radius = bulbRadius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
                )

                // Glossy surface highlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isOn) 0.65f else 0.45f),
                            Color.Transparent
                        ),
                        center = sphereCenter - Offset(bulbRadius * 0.35f, bulbRadius * 0.35f),
                        radius = bulbRadius * 0.38f
                    ),
                    center = sphereCenter - Offset(bulbRadius * 0.35f, bulbRadius * 0.35f),
                    radius = bulbRadius * 0.38f
                )
            }
        }
    }
}

private fun computedAngle(dx: Float, dy: Float): Float {
    return atan2(dx, dy)
}
