package com.slick.btkeyboard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.slick.btkeyboard.databinding.ActivityMainBinding
import com.slick.btkeyboard.databinding.DialogSendTextBinding

/**
 * Single screen that drives [BtHidService]: connect to a host, then type into
 * the capture area with whatever keyboard app is installed on the phone.
 */
class MainActivity : AppCompatActivity(), BtHidService.Listener, KeyCaptureView.Callback {

    private lateinit var binding: ActivityMainBinding

    private var service: BtHidService? = null
    private var bound = false

    /** Modifiers armed for the next keystroke only. */
    private val oneShotModifiers = mutableSetOf<Int>()

    /** Modifiers held down until the user taps them off. */
    private val lockedModifiers = mutableSetOf<Int>()

    /** Guards the toggle-group listener while we update buttons in code. */
    private var updatingModifiers = false

    private val echo = StringBuilder()

    private val modifierBits: Map<Int, Int> by lazy {
        mapOf(
            R.id.modCtrl to HidSpec.MOD_LEFT_CTRL,
            R.id.modShift to HidSpec.MOD_LEFT_SHIFT,
            R.id.modAlt to HidSpec.MOD_LEFT_ALT,
            R.id.modGui to HidSpec.MOD_LEFT_GUI
        )
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, ibinder: IBinder?) {
            service = (ibinder as? BtHidService.LocalBinder)?.service
            service?.setListener(this@MainActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (requiredPermissions().all { granted[it] != false }) {
            startEngine()
        } else {
            toast(getString(R.string.log_permission_required))
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothAdapter()?.isEnabled == true) startEngine()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.captureView.callback = this
        binding.echoText.setOnClickListener { binding.captureView.showKeyboard() }

        binding.startButton.setOnClickListener { requestPermissionsThenStart() }
        binding.connectButton.setOnClickListener { showDevicePicker() }
        binding.disconnectButton.setOnClickListener { service?.disconnect() }
        binding.discoverableButton.setOnClickListener { requestDiscoverable() }
        binding.sendTextButton.setOnClickListener { showBulkTextDialog() }

        setUpModifierButtons()
        buildKeyRow(binding.navKeyRow, NAV_KEYS.map { getString(it.first) to it.second })
        buildKeyRow(binding.functionKeyRow, functionKeys())

        renderState(BtHidService.State.IDLE, null)
    }

    override fun onStart() {
        super.onStart()
        // Re-attach to an already running service without creating a new one.
        if (!bound) bound = bindService(serviceIntent(), connection, 0)
    }

    override fun onStop() {
        super.onStop()
        service?.setListener(null)
        if (bound) {
            unbindService(connection)
            bound = false
            service = null
        }
    }

    // ------------------------------------------------------------- lifecycle

    private fun serviceIntent() = Intent(this, BtHidService::class.java)

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsThenStart() {
        if (!hasPermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }
        startEngine()
    }

    private fun startEngine() {
        val adapter = bluetoothAdapter() ?: run {
            toast(getString(R.string.log_no_bluetooth))
            return
        }
        if (!adapter.isEnabled) {
            toast(getString(R.string.log_bluetooth_off))
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        BtHidService.start(this)
        if (!bound) bound = bindService(serviceIntent(), connection, Context.BIND_AUTO_CREATE)
    }

    @SuppressLint("MissingPermission")
    private fun requestDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
        startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun showDevicePicker() {
        if (!hasPermissions()) {
            requestPermissionsThenStart()
            return
        }
        val devices: List<BluetoothDevice> =
            bluetoothAdapter()?.bondedDevices?.toList().orEmpty()
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.pick_device)
                .setMessage(R.string.no_paired_devices)
                .setPositiveButton(R.string.action_discoverable) { _, _ -> requestDiscoverable() }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            return
        }
        val labels = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_device)
            .setItems(labels) { _, which -> service?.connect(devices[which]) }
            .show()
    }

    private fun showBulkTextDialog() {
        val dialogBinding = DialogSendTextBinding.inflate(LayoutInflater.from(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.send_text_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_send) { _, _ ->
                val text = dialogBinding.bulkText.text?.toString().orEmpty()
                if (text.isNotEmpty()) onText(text)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // -------------------------------------------------------------- keyboard

    private fun setUpModifierButtons() {
        binding.modifierGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updatingModifiers) return@addOnButtonCheckedListener
            val bit = modifierBits[checkedId] ?: return@addOnButtonCheckedListener
            if (isChecked) {
                oneShotModifiers.add(bit)
            } else {
                oneShotModifiers.remove(bit)
                lockedModifiers.remove(bit)
            }
        }
        for ((id, bit) in modifierBits) {
            findViewById<MaterialButton>(id).setOnLongClickListener { button ->
                lockedModifiers.add(bit)
                oneShotModifiers.remove(bit)
                updatingModifiers = true
                binding.modifierGroup.check(id)
                updatingModifiers = false
                toast(getString(R.string.modifier_locked, (button as MaterialButton).text))
                true
            }
        }
    }

    private fun currentModifiers(): Int =
        (oneShotModifiers + lockedModifiers).fold(0) { acc, bit -> acc or bit }

    /** One-shot modifiers only survive a single keystroke. */
    private fun consumeOneShotModifiers() {
        if (oneShotModifiers.isEmpty()) return
        val spent = oneShotModifiers.toList()
        oneShotModifiers.clear()
        updatingModifiers = true
        for ((id, bit) in modifierBits) {
            if (bit in spent) binding.modifierGroup.uncheck(id)
        }
        updatingModifiers = false
    }

    private fun buildKeyRow(row: LinearLayout, keys: List<Pair<String, Int>>) {
        val inflater = LayoutInflater.from(this)
        for ((label, usage) in keys) {
            val button = inflater.inflate(R.layout.item_key_button, row, false) as MaterialButton
            button.text = label
            button.setOnClickListener { sendKey(usage) }
            row.addView(button)
        }
    }

    /** F1..F12 sit at contiguous usages, so the row is generated rather than declared. */
    private fun functionKeys(): List<Pair<String, Int>> =
        (0 until 12).map { "F${it + 1}" to (HidSpec.KEY_F1 + it) }

    private fun sendKey(usage: Int) {
        val hid = service
        if (hid == null || !hid.isConnected) {
            toast(getString(R.string.log_not_connected))
            return
        }
        hid.sendKey(usage, currentModifiers())
        consumeOneShotModifiers()
    }

    // -------------------------------------------- KeyCaptureView.Callback

    override fun onText(text: CharSequence) {
        val hid = service
        if (hid == null || !hid.isConnected) {
            toast(getString(R.string.log_not_connected))
            return
        }
        val modifiers = currentModifiers()
        val dropped = hid.sendText(text, modifiers)
        consumeOneShotModifiers()
        appendEcho(text)
        if (dropped > 0) {
            binding.logText.text = getString(R.string.log_dropped_chars, dropped)
        }
    }

    override fun onKey(usage: Int) {
        sendKey(usage)
        appendEcho(if (usage == HidSpec.KEY_ENTER) "\n" else "")
    }

    override fun onBackspace(count: Int) {
        val hid = service
        if (hid == null || !hid.isConnected) {
            toast(getString(R.string.log_not_connected))
            return
        }
        repeat(count) { hid.sendKey(HidSpec.KEY_BACKSPACE, 0) }
        if (echo.isNotEmpty()) {
            echo.setLength(maxOf(0, echo.length - count))
            binding.echoText.text = echo
        }
    }

    private fun appendEcho(text: CharSequence) {
        if (text.isEmpty()) return
        echo.append(text)
        if (echo.length > ECHO_LIMIT) echo.delete(0, echo.length - ECHO_LIMIT)
        binding.echoText.text = echo
    }

    // ------------------------------------------------ BtHidService.Listener

    override fun onStateChanged(state: BtHidService.State, device: BluetoothDevice?) {
        renderState(state, device)
    }

    override fun onLog(message: String) {
        binding.logText.text = message
    }

    @SuppressLint("MissingPermission")
    private fun renderState(state: BtHidService.State, device: BluetoothDevice?) {
        val name = try {
            device?.name ?: device?.address
        } catch (e: SecurityException) {
            device?.address
        }
        binding.statusText.text = when (state) {
            BtHidService.State.CONNECTED -> getString(R.string.status_connected, name.orEmpty())
            BtHidService.State.CONNECTING -> getString(R.string.status_connecting)
            BtHidService.State.REGISTERED -> getString(R.string.status_ready)
            BtHidService.State.REGISTERING -> getString(R.string.status_starting)
            BtHidService.State.IDLE -> getString(R.string.status_idle)
        }
        val running = state != BtHidService.State.IDLE
        val connected = state == BtHidService.State.CONNECTED
        binding.startButton.isEnabled = !running
        binding.connectButton.isEnabled = running && !connected
        binding.disconnectButton.isEnabled = connected
        binding.captureView.visibility = View.VISIBLE
        if (connected) binding.captureView.showKeyboard()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val DISCOVERABLE_SECONDS = 300
        private const val ECHO_LIMIT = 400

        private val NAV_KEYS = listOf(
            R.string.key_esc to HidSpec.KEY_ESC,
            R.string.key_tab to HidSpec.KEY_TAB,
            R.string.key_backspace to HidSpec.KEY_BACKSPACE,
            R.string.key_enter to HidSpec.KEY_ENTER,
            R.string.key_delete to HidSpec.KEY_DELETE,
            R.string.key_left to HidSpec.KEY_LEFT,
            R.string.key_down to HidSpec.KEY_DOWN,
            R.string.key_up to HidSpec.KEY_UP,
            R.string.key_right to HidSpec.KEY_RIGHT,
            R.string.key_home to HidSpec.KEY_HOME,
            R.string.key_end to HidSpec.KEY_END,
            R.string.key_page_up to HidSpec.KEY_PAGE_UP,
            R.string.key_page_down to HidSpec.KEY_PAGE_DOWN,
            R.string.key_print_screen to HidSpec.KEY_PRINT_SCREEN,
            R.string.key_menu to HidSpec.KEY_APPLICATION
        )
    }
}
