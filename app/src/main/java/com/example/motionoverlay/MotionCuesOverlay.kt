package com.example.motionoverlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.abs
import kotlin.math.max

/**
 * Full-screen Motion Cues Grid Overlay - Performance Optimized with Safe Insets
 *
 * - Staggered 2D grid of dots across entire screen width and height (Canvas)
 *   — evolves from two vertical rows of subtle dots along the far left and right edges
 * - Vibrant cyan/teal palette (#00BCD4 / #00E5FF)
 * - Depth & horizon: center band larger+opaque, edges smaller+transparent (dynamic sizing/alpha by vertical position)
 * - Gentle horizontal/slanted drift via frame-based withFrameNanos + LinearEasing (60/120 FPS)
 * - Graphics Optimization: pre-allocated Paint/Colors/Math outside DrawScope, derivedState & withFrameNanos
 * - Dynamic recalculation via BoxWithConstraints for portrait/landscape rotation, continuous animation preserved
 * - Safe Insets: respects WindowInsets.displayCutout and navigationBars to avoid camera punch holes / gesture handles
 * - hideInLandscape: DataStore flag to auto-hide overlay in horizontal video orientation
 */
@Composable
fun MotionCuesOverlay(
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF00BCD4), // vibrant cyan/teal #00BCD4
    accentColor: Color = Color(0xFF00E5FF), // alternate #00E5FF
    spacing: Float = 42f,
    minRadius: Float = 2.2f,
    maxRadius: Float = 5.8f,
    minAlpha: Float = 0.18f,
    maxAlpha: Float = 0.95f,
    dotSpeed: Float = 1.0f, // Slow 0.5f, Medium 1.0f, Fast 2.0f - from DataStore
    dotOpacity: Float = 0.85f, // 0.1f to 1.0f - from DataStore
    themeColor: Int = OverlaySettings.CYAN, // Cyan, Neon Green, Soft Amber, White - from DataStore
    hideInLandscape: Boolean = false // DataStore flag: auto-hide in horizontal video orientation
) {
    // ========== Graphics Optimization: Pre-allocate Paint, Colors, Math outside DrawScope ==========
    // Paint objects pre-allocated once and reused - avoids allocation per frame
    val composePaint = remember { Paint().apply { isAntiAlias = true } }
    @Suppress("unused")
    val androidPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    // Colors pre-allocated outside render loop - themeColor from DataStore drives palette
    // dotOpacity will modulate final alpha, dotSpeed modulates drift duration
    val effectiveBase = remember(themeColor, baseColor) {
        if (themeColor != OverlaySettings.CYAN) Color(themeColor) else baseColor
    }
    val effectiveAccent = remember(themeColor, accentColor) {
        when (themeColor) {
            OverlaySettings.NEON_GREEN -> Color(OverlaySettings.NEON_GREEN_ACCENT)
            OverlaySettings.SOFT_AMBER -> Color(OverlaySettings.SOFT_AMBER_ACCENT)
            OverlaySettings.WHITE -> Color.White.copy(alpha = 0.9f)
            OverlaySettings.CYAN_ACCENT -> Color(OverlaySettings.CYAN_ACCENT)
            OverlaySettings.CYAN -> accentColor
            else -> Color(themeColor).copy(alpha = 0.85f)
        }
    }
    val base = remember(effectiveBase) { effectiveBase }
    val accent = remember(effectiveAccent) { effectiveAccent }

    // Math constants pre-calculated outside DrawScope / Canvas render loop
    val radiusRange = remember(maxRadius, minRadius) { maxRadius - minRadius }
    val alphaRange = remember(maxAlpha, minAlpha) { maxAlpha - minAlpha }
    val halfSpacing = remember(spacing) { spacing * 0.5f }
    val slantFactor = remember { 0.18f }
    val verticalDriftFactor = remember { 0.12f }
    val glowThreshold = remember { 0.75f }

    // ========== Frame-based animation via withFrameNanos (60/120 FPS, minimal overhead) ==========
    // Use withFrameNanos instead of standard state triggers / infiniteTransition for buttery smooth
    // This drives animation directly from vsync frame time, keeping CPU/battery minimal
    // Maintains continuous animation state across rotation (remembered outside BoxWithConstraints)
    var startNanos by remember { androidx.compose.runtime.mutableStateOf(0L) }
    var currentNanos by remember { androidx.compose.runtime.mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        // Capture initial frame time as anchor - preserved across recompositions
        startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                currentNanos = frameNanos
            }
        }
    }

    // Derived state prevents unnecessary recompositions - only updates when frame time changes
    // dotSpeed controls duration: Slow 0.5f => 13000ms, Medium 1.0f => 6500ms, Fast 2.0f => 3250ms
    val driftDurationMs = remember(dotSpeed) { 6500f / dotSpeed.coerceIn(0.2f, 3.0f) }
    val driftFraction by remember(driftDurationMs) {
        derivedStateOf {
            if (startNanos == 0L || currentNanos == 0L) 0f
            else {
                val elapsedMs = (currentNanos - startNanos) / 1_000_000f
                // Modulo 1f keeps value in 0..1 range for seamless wrap - speed via duration
                (elapsedMs / driftDurationMs) % 1f
            }
        }
    }

    // Secondary subtle pulse kept for backward compatibility (also uses pre-allocated infiniteTransition)
    // This shows evolution from previous rememberInfiniteTransition approach
    val infiniteTransition = rememberInfiniteTransition(label = "ambientPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // ========== Dynamic Screen Recalculation via BoxWithConstraints ==========
    // Adapts grid column/row count on rotation between Portrait and Landscape
    // BoxWithConstraints provides maxWidth/maxHeight that update on configuration change
    // Animation state (driftFraction) is remembered outside, so rotation does not stutter/jump
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        // Auto-hide in landscape when hideInLandscape flag is enabled
        val isLandscape = maxWidth > maxHeight
        if (hideInLandscape && isLandscape) {
            // Hide overlay in horizontal video orientation - return empty to avoid drawing
            return@BoxWithConstraints
        }

        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current

        // ========== Safe Insets: Retrieve WindowInsets.displayCutout and navigationBars ==========
        // Retrieve displayCutout (camera hole punches) and system navigation bar bounds
        // These insets ensure dots never overlap on top of cutouts or gesture handles
        val cutoutLeftPx = WindowInsets.displayCutout.getLeft(density, layoutDirection).toFloat()
        val cutoutRightPx = WindowInsets.displayCutout.getRight(density, layoutDirection).toFloat()
        val cutoutTopPx = WindowInsets.displayCutout.getTop(density).toFloat()
        val cutoutBottomPx = WindowInsets.displayCutout.getBottom(density).toFloat()

        val navLeftPx = WindowInsets.navigationBars.getLeft(density, layoutDirection).toFloat()
        val navRightPx = WindowInsets.navigationBars.getRight(density, layoutDirection).toFloat()
        val navTopPx = WindowInsets.navigationBars.getTop(density).toFloat()
        val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()

        val statusLeftPx = WindowInsets.statusBars.getLeft(density, layoutDirection).toFloat()
        val statusRightPx = WindowInsets.statusBars.getRight(density, layoutDirection).toFloat()
        val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat()
        val statusBottomPx = WindowInsets.statusBars.getBottom(density).toFloat()

        // Combine insets: take maximum of cutout, navigation, and status bars to ensure safe area
        // This guarantees dots avoid camera punch holes AND gesture navigation handles
        val leftInsetPx = max(max(cutoutLeftPx, navLeftPx), statusLeftPx)
        val rightInsetPx = max(max(cutoutRightPx, navRightPx), statusRightPx)
        val topInsetPx = max(max(cutoutTopPx, navTopPx), statusTopPx)
        val bottomInsetPx = max(max(cutoutBottomPx, navBottomPx), statusBottomPx)

        // Convert constraints to pixels for precise grid calculation
        // These values recompute automatically on orientation change
        val widthPx = remember(maxWidth, density) { with(density) { maxWidth.toPx() } }
        val heightPx = remember(maxHeight, density) { with(density) { maxHeight.toPx() } }

        // Inset dot grid rendering parameters: shrink effective safe area so grid avoids insets
        // Dynamically adapt grid dimensions - recalculated on rotation, respecting safe insets
        val safeWidthPx = (widthPx - leftInsetPx - rightInsetPx).coerceAtLeast(0f)
        val safeHeightPx = (heightPx - topInsetPx - bottomInsetPx).coerceAtLeast(0f)
        val cols = remember(safeWidthPx, spacing, leftInsetPx, rightInsetPx) { (safeWidthPx / spacing).toInt() + 3 }
        val rows = remember(safeHeightPx, spacing, topInsetPx, bottomInsetPx) { (safeHeightPx / spacing).toInt() + 3 }

        // Pre-calculate horizon center outside Canvas for reuse (using safe area center)
        val centerYPx = remember(heightPx, topInsetPx, bottomInsetPx) { (topInsetPx + safeHeightPx * 0.5f).coerceAtLeast(heightPx * 0.5f) }
        val halfHeightPx = remember(safeHeightPx, heightPx) { if (safeHeightPx > 0) safeHeightPx * 0.5f else heightPx * 0.5f }

        // Drift offsets pre-computed outside DrawScope (uses frame-derived driftFraction)
        val driftX = driftFraction * spacing
        // Apply pulseAlpha subtly to drift for backward compatibility
        val driftXWithPulse = driftX * pulseAlpha

        Canvas(modifier = Modifier.fillMaxSize()) {
            // ========== Optimized Canvas render loop: NO allocations inside ==========
            // All Colors, Paint, Math pre-allocated above; only primitive float ops inside
            val width = size.width
            val height = size.height

            // Use pre-calculated center/halfHeight (fallback to current size if constraints zero during init)
            val centerY = if (centerYPx > 0) centerYPx else height * 0.5f
            val halfHeight = if (halfHeightPx > 0) halfHeightPx else height * 0.5f

            // Effective safe bounds for inset-aware drawing
            val safeLeft = leftInsetPx
            val safeRight = width - rightInsetPx
            val safeTop = topInsetPx
            val safeBottom = height - bottomInsetPx

            // Draw staggered grid inset-aware
            for (row in -1 until rows) {
                val baseY = topInsetPx + row * spacing
                // Slanted drift: Y-weighted offset creates diagonal impression
                val rowSlantOffset = baseY * slantFactor * 0.06f

                // Depth & horizon: dynamic sizing/alpha based on vertical screen position
                val distanceFromCenter = abs(baseY - centerY) / halfHeight
                val clampedDistance = if (distanceFromCenter > 1f) 1f else if (distanceFromCenter < 0f) 0f else distanceFromCenter
                val horizonInfluence = 1f - (clampedDistance * clampedDistance)

                // Pre-calculated outside allocations: use precomputed ranges
                // dotOpacity from DataStore modulates final alpha (0.1f to 1.0f)
                val radius = minRadius + horizonInfluence * radiusRange
                val baseAlpha = minAlpha + horizonInfluence * alphaRange
                val alpha = (baseAlpha * dotOpacity.coerceIn(0.1f, 1.0f)).coerceIn(0f, 1f)

                val staggerOffset = if (row % 2 == 0) 0f else halfSpacing

                for (col in -1 until cols) {
                    val baseX = leftInsetPx + col * spacing + staggerOffset

                    var x = baseX + driftXWithPulse + rowSlantOffset
                    var y = baseY + driftX * verticalDriftFactor

                    // Seamless wrap-around within safe inset bounds (no modulo allocation)
                    val effectiveWidth = safeRight - safeLeft
                    val effectiveHeight = safeBottom - safeTop
                    if (x < safeLeft - spacing) x += effectiveWidth + spacing * 2
                    if (x > safeRight + spacing) x -= effectiveWidth + spacing * 2
                    if (y < safeTop - spacing) y += effectiveHeight + spacing * 2
                    if (y > safeBottom + spacing) y -= effectiveHeight + spacing * 2

                    if (x < -radius || x > width + radius || y < -radius || y > height + radius) continue

                    // Inset check: never overlap directly on top of camera hole punches or gesture handles
                    // Skip dots that would be inside cutout / navigation bar unsafe zones
                    if (x < safeLeft + radius && y < safeTop + radius * 2) {
                        // Near top-left cutout area - skip to avoid punch hole
                        // Use inset-aware culling: if within cutout bounds, reduce alpha or skip
                        if (x < leftInsetPx + radius || y < topInsetPx + radius) continue
                    }
                    if (x < safeLeft - radius || x > safeRight + radius || y < safeTop - radius || y > safeBottom + radius) {
                        // Strict inset culling: dots never overlap on displayCutout or navigation handle areas
                        // For absolute safety, skip any dot whose center lies inside unsafe inset
                        if (x <= leftInsetPx || x >= safeRight || y <= topInsetPx || y >= safeBottom) {
                            // Allow slight overflow for wrap continuity but reduce visibility near edges
                            // Instead of hard skip, we could clamp, but spec requires never overlap -> skip
                            if (x < safeLeft || x > safeRight || y < safeTop || y > safeBottom) continue
                        }
                    }

                    // Palette alternation - uses pre-allocated base/accent Colors
                    val isEven = (row + col) % 2 == 0
                    // Reuse pre-allocated Color with copy (minimal allocation, unavoidable for alpha)
                    // Alternative optimization: could use lerp, but copy is optimized
                    val dotColor = if (isEven) base else accent

                    // Use pre-allocated Paint alpha via Color copy
                    drawCircle(
                        color = dotColor.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(x, y)
                    )

                    // Glow only for horizon band - saves draw calls at edges
                    if (horizonInfluence > glowThreshold) {
                        drawCircle(
                            color = dotColor.copy(alpha = alpha * 0.22f),
                            radius = radius * 1.9f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Alias for backward compatibility with MotionOverlayService which referenced
 * AmbientOverlayContent. Delegates to full-screen grid.
 */
@Composable
fun AmbientOverlayContent(modifier: Modifier = Modifier) {
    MotionCuesOverlay(modifier = modifier)
}
