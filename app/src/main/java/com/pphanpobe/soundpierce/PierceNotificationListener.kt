package com.pphanpobe.soundpierce

import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * ฟัง notification ของแอปอื่น ๆ เมื่อ LINE (หรือแอปที่เลือก) ส่ง notification
 * และเครื่องอยู่ในโหมดสั่น/เงียบ -> เล่นเสียงเรียกเข้าให้ดัง
 */
class PierceNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val prefs = Prefs(applicationContext)

        if (!prefs.masterEnabled) return

        val pkg = notification.packageName ?: return
        if (!prefs.isPiercePackage(pkg)) return

        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return

        // ในโหมดปกติ แอปจะมีเสียงของมันเองอยู่แล้ว จึงจัดการเฉพาะโหมดสั่น/เงียบ
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            Log.d(TAG, "Pierce sound for $pkg (ringerMode=${am.ringerMode})")
            SoundPlayer.play(applicationContext)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // ไม่ต้องทำอะไร
    }

    companion object {
        private const val TAG = "PierceListener"
    }
}
