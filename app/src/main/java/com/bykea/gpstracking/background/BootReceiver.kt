package com.bykea.gpstracking.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bykea.gpstracking.di.ApplicationScope
import com.bykea.gpstracking.service.StartResult
import com.bykea.gpstracking.service.TrackingController
import com.bykea.gpstracking.service.TrackingNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Restores tracking after a reboot or app update, both of which end the service and clear the
 * system's location requests. Exported only because system broadcasts require it, so any other
 * action is ignored.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: TrackingController
    @Inject lateinit var notification: TrackingNotification
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                if (controller.resumeIfEnabled() == StartResult.NOT_ALLOWED) {
                    notification.postTapToResume()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
