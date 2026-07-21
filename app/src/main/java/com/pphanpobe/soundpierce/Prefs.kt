package com.pphanpobe.soundpierce

import android.content.Context
import android.media.AudioManager

/**
 * เก็บการตั้งค่าของแอปทั้งหมดใน SharedPreferences
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** เปิด/ปิดการทำงานทั้งหมด */
    var masterEnabled: Boolean
        get() = sp.getBoolean(KEY_MASTER, true)
        set(v) = sp.edit().putBoolean(KEY_MASTER, v).apply()

    /** ให้ LINE มีเสียงเรียกเข้าเสมอ */
    var lineEnabled: Boolean
        get() = sp.getBoolean(KEY_LINE, true)
        set(v) = sp.edit().putBoolean(KEY_LINE, v).apply()

    /** LINE: เล่นเสียงเฉพาะสายโทรเข้า (ไม่รวมแจ้งเตือนแชท) */
    var lineCallsOnly: Boolean
        get() = sp.getBoolean(KEY_LINE_CALLS_ONLY, true)
        set(v) = sp.edit().putBoolean(KEY_LINE_CALLS_ONLY, v).apply()

    /** ให้สายโทรเข้าปกติมีเสียงเสมอ */
    var callEnabled: Boolean
        get() = sp.getBoolean(KEY_CALL, true)
        set(v) = sp.edit().putBoolean(KEY_CALL, v).apply()

    /** ระดับเสียงเรียกเข้าเป้าหมาย (%) เมื่อบังคับให้มีเสียง */
    var callVolumePercent: Int
        get() = sp.getInt(KEY_CALL_VOL, 80)
        set(v) = sp.edit().putInt(KEY_CALL_VOL, v.coerceIn(10, 100)).apply()

    /** รายชื่อแพ็กเกจเพิ่มเติม (คั่นด้วย , หรือขึ้นบรรทัดใหม่) */
    var extraPackagesRaw: String
        get() = sp.getString(KEY_EXTRA, "") ?: ""
        set(v) = sp.edit().putString(KEY_EXTRA, v).apply()

    /** ระดับเสียงมีเดียเดิมก่อนถูกดันขึ้น (-1 = ไม่ได้ดันอยู่) ใช้ตอนคืนค่า */
    var savedMediaVolume: Int
        get() = sp.getInt(KEY_SAVED_MEDIA_VOL, -1)
        set(v) = sp.edit().putInt(KEY_SAVED_MEDIA_VOL, v).apply()

    /** ringer mode เดิมก่อนถูกบังคับให้ดัง (ใช้ตอนคืนค่า) */
    var savedRingerMode: Int
        get() = sp.getInt(KEY_SAVED_RINGER, AudioManager.RINGER_MODE_NORMAL)
        set(v) = sp.edit().putInt(KEY_SAVED_RINGER, v).apply()

    /** กำลังบังคับ ringer อยู่หรือไม่ (สำหรับสายโทรเข้า) */
    var ringerOverridden: Boolean
        get() = sp.getBoolean(KEY_OVERRIDDEN, false)
        set(v) = sp.edit().putBoolean(KEY_OVERRIDDEN, v).apply()

    private fun extraPackages(): Set<String> =
        extraPackagesRaw.split(",", "\n", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** ควรทำให้แพ็กเกจนี้มีเสียงหรือไม่ */
    fun isPiercePackage(pkg: String): Boolean {
        if (lineEnabled && (pkg == LINE_PACKAGE || pkg == LINE_LITE_PACKAGE)) return true
        return extraPackages().contains(pkg)
    }

    companion object {
        private const val NAME = "sound_pierce_prefs"
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_LINE = "line_enabled"
        private const val KEY_LINE_CALLS_ONLY = "line_calls_only"
        private const val KEY_CALL = "call_enabled"
        private const val KEY_CALL_VOL = "call_volume_percent"
        private const val KEY_EXTRA = "extra_packages"
        private const val KEY_SAVED_MEDIA_VOL = "saved_media_volume"
        private const val KEY_SAVED_RINGER = "saved_ringer_mode"
        private const val KEY_OVERRIDDEN = "ringer_overridden"

        const val LINE_PACKAGE = "jp.naver.line.android"
        const val LINE_LITE_PACKAGE = "com.linecorp.linelite"
    }
}
