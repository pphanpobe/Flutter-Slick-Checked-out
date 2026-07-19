package com.pphanpobe.soundpierce

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * เล่นเสียงเรียกเข้าผ่าน "usage = ALARM" เพื่อให้ดังแม้เครื่องอยู่ในโหมดสั่น/เงียบ
 * (สตรีม ALARM ไม่ถูกปิดเสียงโดยโหมดสั่น ต่างจากสตรีม RING/NOTIFICATION)
 */
object SoundPlayer {

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var lastPlayAt = 0L

    /**
     * @param durationMs เล่นนานกี่มิลลิวินาที
     * @param debounceMs ระยะเวลาห้ามเล่นซ้ำ (กัน notification ถล่มหลายอันติดกัน)
     */
    @Synchronized
    fun play(context: Context, durationMs: Long = 6000, debounceMs: Long = 3000) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayAt < debounceMs) return
        lastPlayAt = now

        stopInternal()

        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val r = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return

        r.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            r.isLooping = true
        }

        ringtone = r
        try {
            r.play()
        } catch (_: Exception) {
            return
        }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stop() }, durationMs)
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        stopInternal()
    }

    private fun stopInternal() {
        ringtone?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
        }
        ringtone = null
    }
}
