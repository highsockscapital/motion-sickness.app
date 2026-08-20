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
import androidx.compose.material3.SwitchDefaults
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
import com.example.motionoverlay.ui.theme.ButtonBase
import com.example.motionoverlay.ui.theme.ButtonBorder
import com.example.motionoverlay.ui.theme.ButtonText
import com.example.motionoverlay.ui.theme.CardSurface
import com.example.motionoverlay.ui.theme.DarkCharcoal
import com.example.motionoverlay.ui.theme.ItemCardBackground
import com.example.motionoverlay.ui.theme.MotionOverlayTheme
import com.example.motionoverlay.ui.theme.PrimaryAccent
import com.example.motionoverlay.ui.theme.StrokeBorder
import com.example.motionoverlay.ui.theme.WindowBackground
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotionOverlayTheme {
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WindowBackground // #FFFFF0 Ivory
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WindowBackground)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header — Screen Titles & Brand Header: High-contrast, bold dark text #161610
            Text(
                text = "Motion Overlay",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = DarkCharcoal // #161610
            )
            Text(
                text = "Ambient visual horizon anchor",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkCharcoal.copy(alpha = 0.75f)
            )

            // Privacy & Security Banner - Un-dismissable transparency card
            PrivacyBanner()

            // Enable Overlay Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface), // #FFFFFF
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StrokeBorder),
                shape = RoundedCornerShape(16.dp)
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
                            text = "Enable Overlay".uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = DarkCharcoal
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isOverlayEnabled) "Overlay is active" else "Overlay is off",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkCharcoal.copy(alpha = 0.7f)
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
                                        if (context is ComponentActivity) {
                                            context.requestPermissions(
                                                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
                                            )
                                        }
                                    } catch (_: Exception) {}
                                    Toast.makeText(context, "Please grant notification permission, then toggle again", Toast.LENGTH_LONG).show()
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
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                @Suppress("unused")
                                val complianceCheck = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                                @Suppress("unused")
                                val manualCheck = "ACTION_MANUAL_OVERLAY_PERMISSION_REQUEST"
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open overlay settings: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CardSurface,
                            checkedTrackColor = PrimaryAccent,
                            checkedBorderColor = StrokeBorder,
                            uncheckedThumbColor = CardSurface,
                            uncheckedTrackColor = ItemCardBackground,
                            uncheckedBorderColor = StrokeBorder
                        )
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
            val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            if (!isIgnoring) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ItemCardBackground), // #F5F5E6
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StrokeBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Keep overlay smooth during long drives".uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = DarkCharcoal
                        )
                        Text(
                            text = "Battery optimization may pause or throttle the overlay. Disable optimization for Motion Overlay so it remains smooth and uninterrupted during long drives.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkCharcoal.copy(alpha = 0.8f)
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(fallback)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ButtonBase, // #FFFFFF
                                contentColor = ButtonText // #161610
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ButtonBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Disable battery optimization",
                                style = MaterialTheme.typography.labelLarge,
                                color = ButtonText
                            )
                        }
                    }
                }
            }

            // Live Preview Card — Section Title uppercase
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface), // #FFFFFF
                border = androidx.compose.foundation.BorderStroke(1.dp, StrokeBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LIVE PREVIEW".uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = DarkCharcoal
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F1419))
                            .border(1.dp, StrokeBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
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
                        color = DarkCharcoal.copy(alpha = 0.7f)
                    )
                }
            }

            // Speed Slider — Item Card #F5F5E6
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ItemCardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StrokeBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPEED".uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = DarkCharcoal
                        )
                        Text(
                            text = when {
                                settings.dotSpeed < 0.75f -> "Slow"
                                settings.dotSpeed < 1.5f -> "Medium"
                                else -> "Fast"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = PrimaryAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Slow • Medium • Fast",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkCharcoal.copy(alpha = 0.7f)
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
                        steps = 1,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryAccent,
                            activeTrackColor = PrimaryAccent,
                            inactiveTrackColor = CardSurface,
                            activeTickColor = DarkCharcoal,
                            inactiveTickColor = DarkCharcoal.copy(alpha = 0.4f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Slow", style = MaterialTheme.typography.labelSmall, color = DarkCharcoal)
                        Text(text = "Medium", style = MaterialTheme.typography.labelSmall, color = DarkCharcoal)
                        Text(text = "Fast", style = MaterialTheme.typography.labelSmall, color = DarkCharcoal)
                    }
                }
            }

            // Opacity Slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ItemCardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StrokeBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OPACITY".uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = DarkCharcoal
                        )
                        Text(
                            text = "${(settings.dotOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = PrimaryAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "10% to 100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkCharcoal.copy(alpha = 0.7f)
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
                            thumbColor = PrimaryAccent,
                            activeTrackColor = PrimaryAccent,
                            inactiveTrackColor = CardSurface
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "10%", style = MaterialTheme.typography.labelSmall, color = DarkCharcoal)
                        Text(text = "100%", style = MaterialTheme.typography.labelSmall, color = DarkCharcoal)
                    }
                }
            }

            // Color Selector Row
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ItemCardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StrokeBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "THEME COLOR".uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = DarkCharcoal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cyan • Neon Green • Soft Amber • White",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkCharcoal.copy(alpha = 0.7f)
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
                                            color = if (isSelected) StrokeBorder else DarkCharcoal.copy(alpha = 0.3f),
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
                                                .background(PrimaryAccent)
                                                .border(1.dp, StrokeBorder, CircleShape)
                                        )
                                    }
                                }
                                Text(
                                    text = name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DarkCharcoal else DarkCharcoal.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Hide in Landscape Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ItemCardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StrokeBorder),
                shape = RoundedCornerShape(16.dp)
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
                            text = "Hide in landscape".uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = DarkCharcoal
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Auto-hide overlay when switching to horizontal video orientation",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkCharcoal.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = settings.hideInLandscape,
                        onCheckedChange = { checked ->
                            scope.launch {
                                OverlayPreferences.updateHideInLandscape(context, checked)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CardSurface,
                            checkedTrackColor = PrimaryAccent,
                            checkedBorderColor = StrokeBorder,
                            uncheckedThumbColor = CardSurface,
                            uncheckedTrackColor = CardSurface,
                            uncheckedBorderColor = StrokeBorder
                        )
                    )
                }
            }

            // Auto-Dismiss Sleep Timer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ItemCardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StrokeBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "SLEEP TIMER".uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = DarkCharcoal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Automatically stop overlay after selected duration",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkCharcoal.copy(alpha = 0.7f)
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
                                        if (isSelected) PrimaryAccent
                                        else CardSurface
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = StrokeBorder,
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
                                    color = DarkCharcoal
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
                        color = DarkCharcoal.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun PrivacyBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface), // #FFFFFF
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StrokeBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "🔒", style = MaterialTheme.typography.titleMedium, color = DarkCharcoal)
                Text(
                    text = "Privacy & Security".uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = DarkCharcoal
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "🚫", style = MaterialTheme.typography.bodyMedium, color = DarkCharcoal)
                Column {
                    Text(
                        text = "Zero Network Access",
                        style = MaterialTheme.typography.labelLarge,
                        color = DarkCharcoal
                    )
                    Text(
                        text = "The app doesn't have internet permissions and cannot transmit data. No INTERNET permission requested.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkCharcoal.copy(alpha = 0.75f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "🔒", style = MaterialTheme.typography.bodyMedium, color = DarkCharcoal)
                Column {
                    Text(
                        text = "Touch Pass-Through",
                        style = MaterialTheme.typography.labelLarge,
                        color = DarkCharcoal
                    )
                    Text(
                        text = "The overlay is completely click-through and cannot read touches, keystrokes, or screen content. FLAG_NOT_TOUCHABLE + FLAG_NOT_FOCUSABLE with no input channels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkCharcoal.copy(alpha = 0.75f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "📱", style = MaterialTheme.typography.bodyMedium, color = DarkCharcoal)
                Column {
                    Text(
                        text = "100% Local",
                        style = MaterialTheme.typography.labelLarge,
                        color = DarkCharcoal
                    )
                    Text(
                        text = "All visual settings stay stored locally on your device via DataStore. No cloud sync, no analytics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkCharcoal.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
