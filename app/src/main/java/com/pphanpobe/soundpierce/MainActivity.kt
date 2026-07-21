package com.pphanpobe.soundpierce

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var statusNotif: TextView
    private lateinit var statusDnd: TextView
    private lateinit var statusPhone: TextView

    private lateinit var swMaster: SwitchMaterial
    private lateinit var swLine: SwitchMaterial
    private lateinit var swLineCallsOnly: SwitchMaterial
    private lateinit var swCall: SwitchMaterial
    private lateinit var etExtra: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusNotif = findViewById(R.id.status_notif)
        statusDnd = findViewById(R.id.status_dnd)
        statusPhone = findViewById(R.id.status_phone)

        swMaster = findViewById(R.id.sw_master)
        swLine = findViewById(R.id.sw_line)
        swLineCallsOnly = findViewById(R.id.sw_line_calls_only)
        swCall = findViewById(R.id.sw_call)
        etExtra = findViewById(R.id.et_extra)

        swMaster.isChecked = prefs.masterEnabled
        swLine.isChecked = prefs.lineEnabled
        swLineCallsOnly.isChecked = prefs.lineCallsOnly
        swCall.isChecked = prefs.callEnabled
        etExtra.setText(prefs.extraPackagesRaw)

        swMaster.setOnCheckedChangeListener { _, v -> prefs.masterEnabled = v }
        swLine.setOnCheckedChangeListener { _, v -> prefs.lineEnabled = v }
        swLineCallsOnly.setOnCheckedChangeListener { _, v -> prefs.lineCallsOnly = v }
        swCall.setOnCheckedChangeListener { _, v -> prefs.callEnabled = v }

        findViewById<Button>(R.id.btn_notif).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btn_dnd).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        findViewById<Button>(R.id.btn_phone).setOnClickListener {
            requestPhonePermission()
        }
        findViewById<Button>(R.id.btn_test).setOnClickListener {
            SoundPlayer.play(applicationContext, durationMs = 4000, debounceMs = 0)
            Toast.makeText(this, R.string.testing_sound, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_save_extra).setOnClickListener {
            prefs.extraPackagesRaw = etExtra.text.toString()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        maybeRequestPostNotifications()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // กันพลาด: คืนค่าระดับเสียงมีเดียถ้าถูกดันค้างไว้จากรอบก่อน
        SoundPlayer.restoreMediaVolumeIfIdle(this)
    }

    private fun refreshStatus() {
        val notifOk = isNotificationListenerEnabled()
        statusNotif.text = getString(
            if (notifOk) R.string.granted else R.string.not_granted
        )
        statusNotif.setTextColor(colorFor(notifOk))

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndOk = nm.isNotificationPolicyAccessGranted
        statusDnd.text = getString(if (dndOk) R.string.granted else R.string.not_granted)
        statusDnd.setTextColor(colorFor(dndOk))

        val phoneOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        statusPhone.text = getString(if (phoneOk) R.string.granted else R.string.not_granted)
        statusPhone.setTextColor(colorFor(phoneOk))
    }

    private fun colorFor(ok: Boolean): Int =
        ContextCompat.getColor(this, if (ok) R.color.ok_green else R.color.warn_red)

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabled.contains(packageName)
    }

    private fun requestPhonePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_PHONE_STATE),
            REQ_PHONE
        )
    }

    private fun maybeRequestPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIF
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    companion object {
        private const val REQ_PHONE = 1001
        private const val REQ_POST_NOTIF = 1002
    }
}
