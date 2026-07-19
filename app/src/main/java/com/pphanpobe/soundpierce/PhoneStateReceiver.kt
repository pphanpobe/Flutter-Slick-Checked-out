package com.pphanpobe.soundpierce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * ตรวจจับสายโทรเข้า:
 *  - RINGING: ถ้าเครื่องอยู่โหมดสั่น/เงียบ -> บังคับ ringer เป็นปกติ + ตั้งระดับเสียง
 *             เพื่อให้เสียงเรียกเข้าปกติของเครื่องดังตามเดิม
 *  - OFFHOOK/IDLE: คืนค่า ringer mode เดิม
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val prefs = Prefs(context)
        if (!prefs.masterEnabled || !prefs.callEnabled) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> forceRing(prefs, am)
            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> restore(prefs, am)
        }
    }

    private fun forceRing(prefs: Prefs, am: AudioManager) {
        if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) return

        // จำโหมดเดิมไว้เพื่อคืนค่าภายหลัง
        if (!prefs.ringerOverridden) {
            prefs.savedRingerMode = am.ringerMode
            prefs.ringerOverridden = true
        }

        try {
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            val max = am.getStreamMaxVolume(AudioManager.STREAM_RING)
            val target = (max * prefs.callVolumePercent / 100).coerceAtLeast(1)
            am.setStreamVolume(AudioManager.STREAM_RING, target, 0)
            Log.d(TAG, "Forced ring ON (target volume=$target/$max)")
        } catch (e: SecurityException) {
            // ต้องได้สิทธิ์ ACCESS_NOTIFICATION_POLICY (Do Not Disturb access)
            Log.w(TAG, "Cannot change ringer: ${e.message}")
        }
    }

    private fun restore(prefs: Prefs, am: AudioManager) {
        if (!prefs.ringerOverridden) return
        try {
            am.ringerMode = prefs.savedRingerMode
            Log.d(TAG, "Restored ringer mode to ${prefs.savedRingerMode}")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot restore ringer: ${e.message}")
        } finally {
            prefs.ringerOverridden = false
        }
    }

    companion object {
        private const val TAG = "PhoneStateReceiver"
    }
}
