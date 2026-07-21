package com.pphanpobe.soundpierce

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
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
 * และ **ดันระดับเสียงมีเดียขึ้นชั่วคราว** เพื่อให้ดังแม้ผู้ใช้ปิดเสียงจนสุด
 * (จำค่าเดิมไว้แล้วคืนค่าเมื่อเล่นจบ จึงไม่ทิ้งค่าค้าง และไม่ยุ่งกับช่องนาฬิกาปลุก)
 *
 * แยกเป็น 2 กรณี:
 *  - play(): เสียงสั้น ๆ สำหรับ notification เช่น LINE
 *  - startCallRinging()/stopCallRinging(): เสียงวนยาว สำหรับสายโทรเข้า
 */
object SoundPlayer {

    private const val TARGET_PERCENT = 90

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var callPlayer: MediaPlayer? = null
    private var lastPlayAt = 0L
    private var appContext: Context? = null

    private fun soundAttrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun ringtoneUri(context: Context): Uri =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /**
     * เสียงสั้นสำหรับ notification (เช่น LINE chat เมื่อเปิดใช้)
     */
    @Synchronized
    fun play(context: Context, durationMs: Long = 6000, debounceMs: Long = 3000) {
        if (callPlayer != null) return // อย่ารบกวนเสียงสายเข้า

        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayAt < debounceMs) return
        lastPlayAt = now

        appContext = context.applicationContext
        stopRingtoneInternal()
        boostMedia()

        val r = RingtoneManager.getRingtone(appContext, ringtoneUri(context))
        if (r == null) {
            maybeRestore()
            return
        }
        r.audioAttributes = soundAttrs()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            r.isLooping = true
        }
        ringtone = r
        try {
            r.play()
        } catch (_: Exception) {
            ringtone = null
            maybeRestore()
            return
        }
        handler.postDelayed({ onRingtoneTimeout() }, durationMs)
    }

    /**
     * เริ่มเสียงเรียกเข้าแบบวนสำหรับสายโทรเข้า (LINE call / สายปกติ)
     */
    @Synchronized
    fun startCallRinging(context: Context, maxDurationMs: Long = 120000) {
        appContext = context.applicationContext
        stopCallInternal()
        stopRingtoneInternal()
        boostMedia()

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(soundAttrs())
            mp.setDataSource(appContext!!, ringtoneUri(context))
            mp.isLooping = true
            mp.prepare()
            mp.start()
        } catch (_: Exception) {
            try {
                mp.release()
            } catch (_: Exception) {
            }
            maybeRestore()
            return
        }
        callPlayer = mp
        handler.postDelayed({ stopCallRinging() }, maxDurationMs)
    }

    /** หยุดเสียงสายเข้า (เรียกเมื่อรับสาย/วางสาย) */
    @Synchronized
    fun stopCallRinging() {
        stopCallInternal()
        maybeRestore()
    }

    /** หยุดเสียงทั้งหมด */
    @Synchronized
    fun stop() {
        stopRingtoneInternal()
        stopCallInternal()
        maybeRestore()
    }

    /** มีเสียงกำลังเล่นอยู่หรือไม่ */
    @Synchronized
    fun isPlaying(): Boolean = ringtone != null || callPlayer != null

    /**
     * ตัวกันพลาด: ถ้าไม่มีเสียงเล่นอยู่ แต่ค่าระดับเสียงมีเดียยังถูกดันค้างไว้
     * (เช่น process ถูกฆ่ากลางทาง) ให้คืนค่าเดิม
     */
    @Synchronized
    fun restoreMediaVolumeIfIdle(context: Context) {
        if (ringtone != null || callPlayer != null) return
        appContext = context.applicationContext
        restoreMedia()
    }

    // ----- ภายใน -----

    @Synchronized
    private fun onRingtoneTimeout() {
        stopRingtoneInternal()
        maybeRestore()
    }

    private fun stopRingtoneInternal() {
        ringtone?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
        }
        ringtone = null
    }

    private fun stopCallInternal() {
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

    /** คืนค่าระดับเสียงเมื่อไม่มีเสียงเล่นอยู่แล้ว */
    private fun maybeRestore() {
        if (ringtone == null && callPlayer == null) {
            restoreMedia()
        }
    }

    /** ดันระดับเสียงมีเดียขึ้น (จำค่าเดิมไว้ครั้งแรก) */
    private fun boostMedia() {
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = Prefs(ctx)
        if (prefs.savedMediaVolume < 0) {
            prefs.savedMediaVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * TARGET_PERCENT / 100).coerceAtLeast(1)
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: Exception) {
        }
    }

    /** คืนค่าระดับเสียงมีเดียเดิม */
    private fun restoreMedia() {
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = Prefs(ctx)
        val saved = prefs.savedMediaVolume
        if (saved >= 0) {
            try {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
            } catch (_: Exception) {
            }
            prefs.savedMediaVolume = -1
        }
    }
}
