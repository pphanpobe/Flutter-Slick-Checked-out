package com.pphanpobe.soundpierce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * ตรวจจับสายโทรเข้า:
 *  - RINGING: ถ้าเครื่องอยู่โหมดสั่น/เงียบ -> เล่นเสียงเรียกเข้าผ่านสตรีม ALARM
 *             (ดังทะลุโหมดสั่น) โดย **ไม่แตะ ringer mode ของเครื่องเลย**
 *             เครื่องจึงคงเป็นโหมดสั่นเหมือนเดิมตลอด ไม่มีทางค้างเป็นโหมดเสียง
 *  - OFFHOOK (รับสาย) / IDLE (วางสาย/สายหลุด): หยุดเสียง
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val prefs = Prefs(context)
        if (!prefs.masterEnabled || !prefs.callEnabled) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // ในโหมดปกติ เครื่องมีเสียงเรียกเข้าของมันเองอยู่แล้ว จัดการเฉพาะโหมดสั่น/เงียบ
                if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                    Log.d(TAG, "Incoming call in silent/vibrate -> play alarm ringtone")
                    SoundPlayer.startCallRinging(context)
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Call answered/ended -> stop ringtone (state=$state)")
                SoundPlayer.stopCallRinging()
            }
        }
    }

    companion object {
        private const val TAG = "PhoneStateReceiver"
    }
}
