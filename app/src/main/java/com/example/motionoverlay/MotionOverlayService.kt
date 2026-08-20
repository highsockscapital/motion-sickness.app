package com.example.motionoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import android.service.quicksettings.TileService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry

/**
 * Ambient Visual Overlay Service (Toggle-based)
 *
 * - Runs as a persistent foreground service with a "Stop" action button
 * - Attaches a Compose View to WindowManager using TYPE_APPLICATION_OVERLAY
 * - Uses FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE so taps pass through
 *   to underlying apps (no interaction blocking)
 * - No sensor tracking - purely toggle on/off overlay
 */
class MotionOverlayService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "motion_overlay_channel"
        const val CHANNEL_NAME = "Motion Overlay"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.motionoverlay.ACTION_STOP_OVERLAY"
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var overlayView: ComposeView? = null
    private lateinit var windowManager: WindowManager

    // For ComposeView to work in a Service, we need to provide a SavedStateRegistryOwner
    // LifecycleService already provides Lifecycle, but not SavedStateRegistryOwner.
    // We create a small holder that delegates to a controller.
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val savedStateRegistryOwner = object : SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        init {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }
    private val viewModelStore = ViewModelStore()
    private val viewModelStoreOwner = ViewModelStoreOwner { viewModelStore }

    // Auto-dismiss sleep timer - lightweight Coroutine countdown
    private var timerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        attachOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Handle Stop action from notification
        if (intent?.action == ACTION_STOP) {
            cancelTimer()
            stopSelf()
            // Also update tile to OFF
            requestTileUpdateToOff()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        // For Android 14+, specify foregroundServiceType with fallback for target SDK 34+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Schedule lightweight Coroutine countdown for auto-dismiss sleep timer
        // Timer durations: Never (0), 15 Mins, 30 Mins, 1 Hour
        scheduleTimerFromPreferences()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent overlay indicator"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // ========== Auto-Dismiss Sleep Timer (DataStore: Never, 15 Mins, 30 Mins, 1 Hour) ==========
    private fun scheduleTimerFromPreferences() {
        timerJob?.cancel()
        lifecycleScope.launch {
            val settings = OverlayPreferences.overlaySettingsFlow(this@MotionOverlayService).first()
            scheduleTimer(settings.timerDuration)
        }
    }

    private fun scheduleTimer(durationMinutes: Int) {
        timerJob?.cancel()
        if (durationMinutes == OverlaySettings.TIMER_NEVER || durationMinutes <= 0) {
            return // Never - no timer
        }
        val millis = OverlaySettings.timerDurationMillis(durationMinutes)
        if (millis <= 0L) return
        // Schedule lightweight Coroutine countdown corresponding to chosen duration
        timerJob = lifecycleScope.launch {
            delay(millis)
            // Automatically stop the foreground service when timer expires
            isRunning = false
            stopSelf()
            // Update Quick Settings Tile state to OFF when timer expires
            requestTileUpdateToOff()
        }
    }

    private fun requestTileUpdateToOff() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                TileService.requestListeningState(
                    applicationContext,
                    ComponentName(applicationContext, MotionTileService::class.java)
                )
            }
        } catch (_: Exception) {
            // Ignore if TileService not available
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun createNotification(): Notification {
        // Intent to stop the service via notification action
        val stopIntent = Intent(this, MotionOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Optional: tap to open app
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Motion Overlay Active")
            .setContentText("Ambient visual overlay is showing")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            // Persistent foreground notification with a "Stop" action button
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun attachOverlay() {
        if (overlayView != null) return

        // Strict privacy: overlay must be purely visual - no input capture
        // Verify WindowManager.LayoutParams strictly includes FLAG_NOT_TOUCHABLE and FLAG_NOT_FOCUSABLE
        // Explicitly ensure NO input channels or touch listener capabilities are attached
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        val composeView = ComposeView(this).apply {
            // Required so Compose can observe lifecycle in a Service context
            setViewTreeLifecycleOwner(this@MotionOverlayService)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            // ViewModelStore is needed for viewModel() support inside overlay
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            // Privacy enforcement: Explicitly ensure NO input channels or touch listeners
            // DO NOT set onTouchListener, onClickListener, or focusable - overlay must remain click-through
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false

            setContent {
                // Service Synchronization: expose saved preferences as Flow so rendering updates dynamically
                val settingsFlow = remember { OverlayPreferences.overlaySettingsFlow(this@MotionOverlayService) }
                val settings by settingsFlow.collectAsState(initial = OverlaySettings())
                MotionCuesOverlay(
                    dotSpeed = settings.dotSpeed,
                    dotOpacity = settings.dotOpacity,
                    themeColor = settings.themeColor,
                    hideInLandscape = settings.hideInLandscape
                )
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    private fun detachOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: IllegalArgumentException) {
                // View not attached
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        cancelTimer()
        isRunning = false
        detachOverlay()
        viewModelStore.clear()
        // Ensure tile reflects OFF state
        requestTileUpdateToOff()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
