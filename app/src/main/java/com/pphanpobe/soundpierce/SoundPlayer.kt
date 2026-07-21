package com.pphanpobe.soundpierce

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * เล่นเสียงเรียกเข้าผ่าน "usage = MEDIA" เพื่อให้ดังแม้เครื่องอยู่ในโหมดสั่น/เงียบ
 * (สตรีม MEDIA ไม่ถูกปิดเสียงโดยโหมดสั่น เช่นเดียวกับ ALARM แต่ **ไม่ไปยุ่ง
 *  กับช่องนาฬิกาปลุกของระบบ** จึงไม่ทำให้เสียงปลุกจริงเงียบ)
 *
 * แยกเป็น 2 กรณี:
 *  - play(): เสียงสั้น ๆ สำหรับ notification เช่น LINE
 *  - startCallRinging()/stopCallRinging(): เสียงวนยาว สำหรับสายโทรเข้า
 *    (วนจนกว่าจะรับสาย/วางสาย โดยไม่แตะ ringer mode ของเครื่อง)
 */
object SoundPlayer {

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var callPlayer: MediaPlayer? = null
    private var lastPlayAt = 0L

    private fun soundAttrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun ringtoneUri(context: Context): Uri =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /**
     * เสียงสั้นสำหรับ notification (เช่น LINE)
     * @param durationMs เล่นนานกี่มิลลิวินาที
     * @param debounceMs ระยะเวลาห้ามเล่นซ้ำ (กัน notification ถล่มหลายอันติดกัน)
     */
    @Synchronized
    fun play(context: Context, durationMs: Long = 6000, debounceMs: Long = 3000) {
        // ถ้ากำลังมีเสียงสายเข้าอยู่ อย่ารบกวน
        if (callPlayer != null) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayAt < debounceMs) return
        lastPlayAt = now

        stopRingtone()

        val r = RingtoneManager.getRingtone(context.applicationContext, ringtoneUri(context)) ?: return
        r.audioAttributes = soundAttrs()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            r.isLooping = true
        }
        ringtone = r
        try {
            r.play()
        } catch (_: Exception) {
            return
        }
        handler.postDelayed({ stopRingtone() }, durationMs)
    }

    /**
     * เริ่มเสียงเรียกเข้าแบบวนสำหรับสายโทรเข้า (ไม่แตะ ringer mode)
     * ใช้ MediaPlayer เพื่อให้ loop ได้ทุกเวอร์ชัน Android
     * @param maxDurationMs เพดานเวลาเผื่อกันค้าง (หยุดอัตโนมัติ)
     */
    @Synchronized
    fun startCallRinging(context: Context, maxDurationMs: Long = 120000) {
        stopCallRinging()
        stopRingtone()

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(soundAttrs())
            mp.setDataSource(context.applicationContext, ringtoneUri(context))
            mp.isLooping = true
            mp.prepare()
            mp.start()
        } catch (_: Exception) {
            try {
                mp.release()
            } catch (_: Exception) {
            }
            return
        }
        callPlayer = mp
        handler.postDelayed({ stopCallRinging() }, maxDurationMs)
    }

    /** หยุดเสียงสายเข้า (เรียกเมื่อรับสาย/วางสาย) */
    @Synchronized
    fun stopCallRinging() {
        callPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        callPlayer = null
    }

    /** หยุดเสียงทั้งหมด */
    @Synchronized
    fun stop() {
        stopRingtone()
        stopCallRinging()
    }

    private fun stopRingtone() {
        ringtone?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
        }
        ringtone = null
    }
}
