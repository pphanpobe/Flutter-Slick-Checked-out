package com.pphanpobe.soundpierce

import android.app.Notification
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * ฟัง notification ของแอปอื่น ๆ เมื่อ LINE (หรือแอปที่เลือก) ส่ง notification
 * และเครื่องอยู่ในโหมดสั่น/เงียบ -> เล่นเสียงให้ดัง
 *
 * แยกประเภท notification:
 *  - "สายโทรเข้า" (category=call หรือมี fullScreenIntent) -> เล่นเสียงวนยาวจนกว่าสายจะหาย
 *  - "แจ้งเตือนทั่วไป/แชท" -> เล่นเสียงสั้น (และข้ามได้ถ้าตั้งค่า LINE เฉพาะสายโทร)
 */
class PierceNotificationListener : NotificationListenerService() {

    /** key ของ notification สายโทรที่กำลังเล่นเสียงอยู่ */
    private var activeCallKey: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val prefs = Prefs(applicationContext)
        if (!prefs.masterEnabled) return

        val pkg = notification.packageName ?: return
        if (!prefs.isPiercePackage(pkg)) return

        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        // ในโหมดปกติ แอปมีเสียงของมันเองอยู่แล้ว จัดการเฉพาะโหมดสั่น/เงียบ
        if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) return

        val isLine = pkg == Prefs.LINE_PACKAGE || pkg == Prefs.LINE_LITE_PACKAGE
        val isCall = isCallNotification(notification.notification)

        if (isCall) {
            // สายโทรเข้า -> เล่นเสียงวนยาว จนกว่า notification จะหาย
            if (notification.key == activeCallKey) return // กำลังเล่นอยู่แล้ว
            activeCallKey = notification.key
            Log.d(TAG, "Incoming CALL notification from $pkg -> ring")
            SoundPlayer.startCallRinging(applicationContext)
            return
        }

        // notification ทั่วไป (แชท ฯลฯ)
        if (isLine && prefs.lineCallsOnly) {
            // ผู้ใช้ต้องการเฉพาะสายโทรเข้า LINE -> ข้ามแชท
            return
        }
        Log.d(TAG, "Notification from $pkg -> short sound")
        SoundPlayer.play(applicationContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val removed = sbn ?: return
        // สายโทรหาย (รับสาย/วางสาย/ตัดสาย) -> หยุดเสียง
        if (removed.key == activeCallKey) {
            Log.d(TAG, "Call notification removed -> stop ring")
            activeCallKey = null
            SoundPlayer.stopCallRinging()
        }
    }

    /**
     * เป็น notification ของ "สายโทรเข้า" หรือไม่
     * สายโทร (รวม LINE call) มักตั้ง category = "call" และ/หรือมี fullScreenIntent
     * เพื่อแสดงหน้าจอรับสายเต็มจอ ต่างจากแจ้งเตือนแชททั่วไป
     */
    private fun isCallNotification(n: Notification): Boolean {
        if (n.category == Notification.CATEGORY_CALL) return true
        if (n.fullScreenIntent != null) return true
        return false
    }

    companion object {
        private const val TAG = "PierceListener"
    }
}
