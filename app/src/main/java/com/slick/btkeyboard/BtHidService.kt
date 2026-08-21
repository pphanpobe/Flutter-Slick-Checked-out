package com.slick.btkeyboard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * Owns the Bluetooth HID Device profile: registers the phone as a keyboard
 * peripheral, keeps the link to the host alive and serialises outgoing reports.
 *
 * Runs in the foreground so the connection survives while the user switches to
 * another app (or the screen turns off) mid-typing.
 */
class BtHidService : Service() {

    enum class State { IDLE, REGISTERING, REGISTERED, CONNECTING, CONNECTED }

    interface Listener {
        fun onStateChanged(state: State, device: BluetoothDevice?)
        fun onLog(message: String)
    }

    inner class LocalBinder : Binder() {
        val service: BtHidService get() = this@BtHidService
    }

    private val binder = LocalBinder()
    private val main = Handler(Looper.getMainLooper())

    /** Reports are sent from one worker thread so they can never interleave. */
    private val sender = Executors.newSingleThreadExecutor()

    /** Profile callbacks get their own thread so they never queue behind reports. */
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private var adapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var listener: Listener? = null

    /** The last report we sent, replayed when the host issues a GET_REPORT. */
    private val currentReport = ByteArray(HidSpec.REPORT_SIZE)

    @Volatile
    private var bootProtocol = false

    var state: State = State.IDLE
        private set

    var connectedDevice: BluetoothDevice? = null
        private set

    // ------------------------------------------------------------------ setup

    override fun onCreate() {
        super.onCreate()
        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        openProfileProxy()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        unregisterApp()
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
        sender.shutdownNow()
        callbackExecutor.shutdownNow()
        super.onDestroy()
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
        listener?.onStateChanged(state, connectedDevice)
    }

    // -------------------------------------------------------------- profile

    private fun openProfileProxy() {
        val adapter = adapter ?: run {
            log(getString(R.string.log_no_bluetooth))
            return
        }
        if (hidDevice != null) return
        setState(State.REGISTERING, null)
        adapter.getProfileProxy(this, proxyListener, BluetoothProfile.HID_DEVICE)
    }

    private val proxyListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = proxy as BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = null
            setState(State.IDLE, null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val hid = hidDevice ?: return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            getString(R.string.hid_name),
            getString(R.string.hid_description),
            getString(R.string.hid_provider),
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HidSpec.REPORT_DESCRIPTOR
        )
        val ok = try {
            hid.registerApp(sdp, null, null, callbackExecutor, hidCallback)
        } catch (e: SecurityException) {
            log(getString(R.string.log_permission_missing))
            Log.w(TAG, "registerApp denied", e)
            false
        }
        if (!ok) {
            log(getString(R.string.log_register_failed))
            setState(State.IDLE, null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun unregisterApp() {
        val hid = hidDevice ?: return
        try {
            connectedDevice?.let { hid.disconnect(it) }
            hid.unregisterApp()
        } catch (e: SecurityException) {
            Log.w(TAG, "unregisterApp denied", e)
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registered) {
                setState(State.REGISTERED, null)
                log(getString(R.string.log_registered))
                pluggedDevice?.let { connect(it) }
            } else {
                setState(State.IDLE, null)
                log(getString(R.string.log_unregistered))
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    setState(State.CONNECTED, device)
                }

                BluetoothProfile.STATE_CONNECTING -> setState(State.CONNECTING, device)

                else -> {
                    connectedDevice = null
                    bootProtocol = false
                    currentReport.fill(0)
                    setState(if (hidDevice != null) State.REGISTERED else State.IDLE, null)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            // The host is asking for the current key state; hand back the last
            // report we sent so it can resynchronise.
            try {
                hidDevice?.replyReport(device, type, id, currentReport.copyOf())
            } catch (e: SecurityException) {
                Log.w(TAG, "replyReport denied", e)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // LED state (caps/num lock). Nothing to do, but acknowledging keeps
            // some hosts from retrying.
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            bootProtocol = protocol == BluetoothHidDevice.PROTOCOL_BOOT_MODE
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            connectedDevice = null
            setState(State.REGISTERED, null)
            log(getString(R.string.log_unplugged))
        }
    }

    // ------------------------------------------------------------ connection

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val hid = hidDevice ?: run {
            log(getString(R.string.log_not_registered))
            return
        }
        setState(State.CONNECTING, device)
        try {
            if (!hid.connect(device)) {
                log(getString(R.string.log_connect_failed))
                setState(State.REGISTERED, null)
            }
        } catch (e: SecurityException) {
            log(getString(R.string.log_permission_missing))
            setState(State.REGISTERED, null)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val hid = hidDevice ?: return
        val device = connectedDevice ?: return
        try {
            hid.disconnect(device)
        } catch (e: SecurityException) {
            Log.w(TAG, "disconnect denied", e)
        }
    }

    // ----------------------------------------------------------------- input

    val isConnected: Boolean get() = state == State.CONNECTED && connectedDevice != null

    /**
     * Presses and releases a single key.
     *
     * @param usage HID usage from [HidSpec]
     * @param modifiers modifier bitmask applied for the duration of the press
     */
    fun sendKey(usage: Int, modifiers: Int) {
        if (usage <= 0) return
        val report = ByteArray(HidSpec.REPORT_SIZE)
        report[0] = modifiers.toByte()
        report[2] = usage.toByte()
        post(report)
        post(ByteArray(HidSpec.REPORT_SIZE))
    }

    /** Holds only the modifier keys, e.g. to let the host show a Win/Cmd overlay. */
    fun sendModifiersOnly(modifiers: Int) {
        val report = ByteArray(HidSpec.REPORT_SIZE)
        report[0] = modifiers.toByte()
        post(report)
    }

    /**
     * Types one character.
     *
     * @return false if the character has no key position on the US or Thai
     *   layout, so the caller can tell the user it was dropped.
     */
    fun sendChar(c: Char, extraModifiers: Int = 0): Boolean {
        val packed = UsKeymap.forChar(c)
        if (packed == UsKeymap.NONE) return false
        var modifiers = extraModifiers
        if (UsKeymap.needsShift(packed)) modifiers = modifiers or HidSpec.MOD_LEFT_SHIFT
        sendKey(UsKeymap.usage(packed), modifiers)
        return true
    }

    /** Types a string; returns the number of characters that could not be sent. */
    fun sendText(text: CharSequence, extraModifiers: Int = 0): Int {
        var dropped = 0
        for (c in text) {
            if (!sendChar(c, extraModifiers)) dropped++
        }
        return dropped
    }

    private fun post(report: ByteArray) {
        val hid = hidDevice ?: return
        val device = connectedDevice ?: return
        sender.execute {
            try {
                val reportId = if (bootProtocol) 0 else HidSpec.REPORT_ID_KEYBOARD
                hid.sendReport(device, reportId, report)
                System.arraycopy(report, 0, currentReport, 0, HidSpec.REPORT_SIZE)
                // Hosts drop reports that arrive back-to-back with no gap.
                Thread.sleep(REPORT_GAP_MS)
            } catch (e: SecurityException) {
                Log.w(TAG, "sendReport denied", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    // ---------------------------------------------------------- notification

    private fun setState(state: State, device: BluetoothDevice?) {
        this.state = state
        main.post {
            updateNotification()
            listener?.onStateChanged(state, device ?: connectedDevice)
        }
    }

    private fun log(message: String) {
        main.post { listener?.onLog(message) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val text = when (state) {
            State.CONNECTED -> getString(R.string.status_connected_short)
            State.CONNECTING -> getString(R.string.status_connecting)
            State.REGISTERED -> getString(R.string.status_ready)
            State.REGISTERING -> getString(R.string.status_starting)
            State.IDLE -> getString(R.string.status_idle)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_keyboard)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val TAG = "BtHidService"
        private const val CHANNEL_ID = "bt_hid_keyboard"
        private const val NOTIFICATION_ID = 42
        private const val REPORT_GAP_MS = 6L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BtHidService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BtHidService::class.java))
        }
    }
}
