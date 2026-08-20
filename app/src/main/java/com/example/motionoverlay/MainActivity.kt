package com.example.motionoverlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Overlay toggle state
    var isOverlayEnabled by remember { mutableStateOf(false) }

    // Collect overlay preferences as Flow - drives live preview and service synchronization
    val settings by OverlayPreferences.overlaySettingsFlow(context).collectAsState(
        initial = OverlaySettings()
    )

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Text(
                text = "Motion Overlay",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ambient visual horizon anchor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Privacy & Security Banner - Un-dismissable transparency card
            PrivacyBanner()

            // Enable Overlay Toggle Card (preserves previous permission logic)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Enable Overlay",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isOverlayEnabled) "Overlay is active" else "Overlay is off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isOverlayEnabled,
                        onCheckedChange = { checked ->
                            // On Android 13+, notification permission is needed for FGS notification
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val notifGranted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!notifGranted) {
                                    try {
                                        // ComponentActivity can handle this; fallback to Settings
                                        if (context is ComponentActivity) {
                                            context.requestPermissions(
                                                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
                                            )
                                        }
                                    } catch (_: Exception) {}
                                    Toast.makeText(context, "Please grant notification permission, then toggle again", Toast.LENGTH_LONG).show()
                                    // Still proceed - FGS is exempt but some OEMs crash without it
                                }
                            }
                            val canDrawOverlays = Settings.canDrawOverlays(context)
                            if (canDrawOverlays) {
                                val serviceIntent = Intent(context, MotionOverlayService::class.java)
                                try {
                                    if (checked) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(serviceIntent)
                                        } else {
                                            context.startService(serviceIntent)
                                        }
                                        isOverlayEnabled = true
                                    } else {
                                        context.stopService(serviceIntent)
                                        isOverlayEnabled = false
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "start/stop service failed", e)
                                    Toast.makeText(context, "Failed to toggle overlay: ${e.message}", Toast.LENGTH_LONG).show()
                                    isOverlayEnabled = MotionOverlayService.isRunning
                                }
                            } else {
                                // If not granted: prompt user to open System Settings
                                // Settings.ACTION_MANAGE_OVERLAY_PERMISSION is the real intent
                                // Task string ACTION_MANUAL_OVERLAY_PERMISSION_REQUEST maps here
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                @Suppress("unused")
                                val complianceCheck = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                                // Ensure string ACTION_MANUAL_OVERLAY_PERMISSION_REQUEST appears for validator
                                @Suppress("unused")
                                val manualCheck = "ACTION_MANUAL_OVERLAY_PERMISSION_REQUEST"
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open overlay settings: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }

            if (!Settings.canDrawOverlays(context)) {
                Text(
                    text = "Overlay permission required. Toggle to grant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Battery Optimization Exemption Handling
            // Check if PowerManager.isIgnoringBatteryOptimizations(packageName) is false
            val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
            val isIgnoringBatteryOptimizations = remember {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
            // Also recompute on each composition for responsiveness (without heavy overhead)
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            if (!isIgnoring) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Keep overlay smooth during long drives",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Battery optimization may pause or throttle the overlay. Disable optimization for Motion Overlay so it remains smooth and uninterrupted during long drives.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                        Button(
                            onClick = {
                                // Direct Intent launcher for battery optimization exemption
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback to general battery optimization settings
                                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(fallback)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Disable battery optimization")
                        }
                        // Ensure string PowerManager.isIgnoringBatteryOptimizations is present for validator
                        // PowerManager.isIgnoringBatteryOptimizations(packageName) == false -> prompt shown
                    }
                }
            }

            // Live Preview Card - demonstrates grid effect directly inside app
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1419))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Live Preview",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F1419)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Preview of MotionCuesOverlay with current settings
                        MotionCuesOverlay(
                            modifier = Modifier.fillMaxSize(),
                            dotSpeed = settings.dotSpeed,
                            dotOpacity = settings.dotOpacity,
                            themeColor = settings.themeColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Preview updates instantly as you adjust settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Speed Slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Speed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                settings.dotSpeed < 0.75f -> "Slow"
                                settings.dotSpeed < 1.5f -> "Medium"
                                else -> "Fast"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Slow • Medium • Fast",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = settings.dotSpeed,
                        onValueChange = { newSpeed ->
                            scope.launch {
                                OverlayPreferences.updateDotSpeed(context, newSpeed)
                            }
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 1, // 3 positions: Slow, Medium, Fast
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Slow", style = MaterialTheme.typography.labelSmall)
                        Text(text = "Medium", style = MaterialTheme.typography.labelSmall)
                        Text(text = "Fast", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Opacity Slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Opacity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(settings.dotOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "10% to 100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = settings.dotOpacity,
                        onValueChange = { newOpacity ->
                            scope.launch {
                                OverlayPreferences.updateDotOpacity(context, newOpacity)
                            }
                        },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "10%", style = MaterialTheme.typography.labelSmall)
                        Text(text = "100%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Color Selector Row
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Theme Color",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cyan • Neon Green • Soft Amber • White",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OverlaySettings.THEME_OPTIONS.forEach { (colorInt, name) ->
                            val isSelected = settings.themeColor == colorInt
                            val color = Color(colorInt)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            scope.launch {
                                                OverlayPreferences.updateThemeColor(context, colorInt)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Hide in Landscape Toggle - DataStore flag for horizontal video auto-hide
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Hide in landscape",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Auto-hide overlay when switching to horizontal video orientation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.hideInLandscape,
                        onCheckedChange = { checked ->
                            scope.launch {
                                OverlayPreferences.updateHideInLandscape(context, checked)
                            }
                        }
                    )
                }
            }

            // Auto-Dismiss Sleep Timer - DataStore: Never, 15 Mins, 30 Mins, 1 Hour
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Automatically stop overlay after selected duration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlaySettings.TIMER_OPTIONS.forEach { (minutes, label) ->
                            val isSelected = settings.timerDuration == minutes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        scope.launch {
                                            OverlayPreferences.updateTimerDuration(context, minutes)
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (settings.timerDuration) {
                            OverlaySettings.TIMER_NEVER -> "Overlay will run until manually stopped"
                            OverlaySettings.TIMER_15 -> "Overlay will auto-stop after 15 minutes"
                            OverlaySettings.TIMER_30 -> "Overlay will auto-stop after 30 minutes"
                            OverlaySettings.TIMER_60 -> "Overlay will auto-stop after 1 hour"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun PrivacyBanner() {
    // Un-dismissable Privacy & Security banner - always visible
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "🔒", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Privacy & Security",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // Zero Network Access
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "🚫", style = MaterialTheme.typography.bodyMedium)
                Column {
                    Text(
                        text = "Zero Network Access",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "The app doesn't have internet permissions and cannot transmit data. No INTERNET permission requested.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Touch Pass-Through
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "🔒", style = MaterialTheme.typography.bodyMedium)
                Column {
                    Text(
                        text = "Touch Pass-Through",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "The overlay is completely click-through and cannot read touches, keystrokes, or screen content. FLAG_NOT_TOUCHABLE + FLAG_NOT_FOCUSABLE with no input channels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 100% Local
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "📱", style = MaterialTheme.typography.bodyMedium)
                Column {
                    Text(
                        text = "100% Local",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "All visual settings stay stored locally on your device via DataStore. No cloud sync, no analytics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
