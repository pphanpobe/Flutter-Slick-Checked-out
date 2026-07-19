package com.pphanpobe.soundpierce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * หลังบูตเครื่อง เคลียร์ธง ringerOverridden ที่อาจค้างไว้
 * (NotificationListenerService และ PhoneStateReceiver จะถูกระบบเรียกเองตามเหตุการณ์)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Prefs(context).ringerOverridden = false
        }
    }
}
