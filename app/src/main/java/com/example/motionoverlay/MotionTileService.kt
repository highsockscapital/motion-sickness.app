package com.example.motionoverlay

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings Tile for toggling the Motion Overlay directly from
 * the notification shade.
 *
 * - onStartListening: reflects MotionOverlayService running state
 * - onClick: checks overlay permission, toggles service or launches MainActivity via unlockAndRun
 */
class MotionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        // Check if system overlay permissions are granted
        val hasOverlayPermission = Settings.canDrawOverlays(this)

        if (!hasOverlayPermission) {
            // If NOT granted: collapse the status bar and launch MainActivity so user can grant permission
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Use unlockAndRun to collapse shade and launch activity after unlock
            // For API 34+ prefer startActivityAndCollapse with PendingIntent
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    // Collapse shade and launch MainActivity
                    startActivityAndCollapse(pendingIntent)
                } else {
                    // Fallback: unlockAndRun with Runnable that starts MainActivity
                    unlockAndRun {
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                // Ultimate fallback
                unlockAndRun {
                    startActivity(intent)
                }
            }
            return
        }

        // If granted: Toggle MotionOverlayService and update tile visual state immediately
        val isRunning = isOverlayServiceRunning()
        val serviceIntent = Intent(this, MotionOverlayService::class.java)

        if (isRunning) {
            stopService(serviceIntent)
            // Update tile to INACTIVE immediately
            qsTile?.let { tile ->
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            // Update tile to ACTIVE immediately
            qsTile?.let { tile ->
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
            }
        }
        // Ensure tile reflects new state on next listening cycle as well
        // updateTileState() will also be called via onStartListening, but immediate update improves UX
    }

    /**
     * Update tile state based on whether MotionOverlayService is currently running.
     * Called in onStartListening and after toggle.
     */
    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = isOverlayServiceRunning()
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        // Optional: keep icon consistent (set in manifest as @drawable/ic_motion_cue)
        // tile.icon = Icon.createWithResource(this, R.drawable.ic_motion_cue)
        tile.updateTile()
    }

    /**
     * Check if MotionOverlayService is currently running.
     * Primary check via static isRunning flag; fallback to ActivityManager for robustness.
     */
    private fun isOverlayServiceRunning(): Boolean {
        // Fast path: static flag maintained by MotionOverlayService
        if (MotionOverlayService.isRunning) return true

        // Fallback: query ActivityManager (deprecated but still works for own services)
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            manager.getRunningServices(Integer.MAX_VALUE).any {
                it.service.className == MotionOverlayService::class.java.name
            }
        } catch (e: Exception) {
            false
        }
    }
}
