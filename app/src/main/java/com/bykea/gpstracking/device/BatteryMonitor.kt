package com.bykea.gpstracking.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.bykea.gpstracking.domain.model.BatteryState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reports battery level, charging and Power Saver, which are what make tracking back off to protect
 * the phone. Listens to broadcasts rather than polling, so it costs nothing between changes.
 */
class BatteryMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun states(): Flow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read())
            }
        }
        // All four are protected system broadcasts, so no export flag is needed.
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            },
        )
        trySend(read())
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun read(): BatteryState {
        // A null receiver just reads the sticky broadcast.
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return BatteryState(
            // An unreadable battery must not look critically low and throttle tracking.
            levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else 100,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            isPowerSaveMode = context.getSystemService<PowerManager>()?.isPowerSaveMode ?: false,
        )
    }
}
