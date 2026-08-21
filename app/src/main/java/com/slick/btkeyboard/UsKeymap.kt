package com.slick.btkeyboard

import android.view.KeyEvent

/**
 * Which keyboard layout the *host* must be switched to for a character to come
 * out right. A Bluetooth keyboard sends key positions, so the same key produces
 * a different glyph depending on this.
 */
enum class HostLayout { US, THAI }

/**
 * Translates printable characters and Android key codes into HID usages.
 *
 * A resolved key is packed into a single Int: the low byte is the HID usage and
 * bit 8 marks "needs shift". [NONE] means the character cannot be produced.
 */
object UsKeymap {

    const val NONE = -1
    private const val SHIFT = 0x100

    fun usage(packed: Int): Int = packed and 0xFF

    fun needsShift(packed: Int): Boolean = (packed and SHIFT) != 0

    private val charMap: Map<Char, Int> = buildMap {
        // a..z -> 0x04..0x1D
        for (c in 'a'..'z') put(c, 0x04 + (c - 'a'))
        for (c in 'A'..'Z') put(c, (0x04 + (c - 'A')) or SHIFT)

        // 1..9 -> 0x1E..0x26, 0 -> 0x27
        for (c in '1'..'9') put(c, 0x1E + (c - '1'))
        put('0', 0x27)

        put('!', 0x1E or SHIFT)
        put('@', 0x1F or SHIFT)
        put('#', 0x20 or SHIFT)
        put('$', 0x21 or SHIFT)
        put('%', 0x22 or SHIFT)
        put('^', 0x23 or SHIFT)
        put('&', 0x24 or SHIFT)
        put('*', 0x25 or SHIFT)
        put('(', 0x26 or SHIFT)
        put(')', 0x27 or SHIFT)

        put('\n', HidSpec.KEY_ENTER)
        put('\r', HidSpec.KEY_ENTER)
        put('\u001B', HidSpec.KEY_ESC)
        put('\b', HidSpec.KEY_BACKSPACE)
        put('\t', HidSpec.KEY_TAB)
        put(' ', HidSpec.KEY_SPACE)

        put('-', 0x2D); put('_', 0x2D or SHIFT)
        put('=', 0x2E); put('+', 0x2E or SHIFT)
        put('[', 0x2F); put('{', 0x2F or SHIFT)
        put(']', 0x30); put('}', 0x30 or SHIFT)
        put('\\', 0x31); put('|', 0x31 or SHIFT)
        put(';', 0x33); put(':', 0x33 or SHIFT)
        put('\'', 0x34); put('"', 0x34 or SHIFT)
        put('`', 0x35); put('~', 0x35 or SHIFT)
        put(',', 0x36); put('<', 0x36 or SHIFT)
        put('.', 0x37); put('>', 0x37 or SHIFT)
        put('/', 0x38); put('?', 0x38 or SHIFT)
    }

    /**
     * Resolves a character to a packed key.
     *
     * Non-ASCII characters fall through to the Thai Kedmanee table, which maps a
     * Thai character onto the US key position that produces it while the host is
     * switched to the Thai layout.
     */
    fun forChar(c: Char): Int {
        charMap[c]?.let { return it }
        ThaiKedmanee.toUsChar(c)?.let { usChar -> charMap[usChar]?.let { return it } }
        return NONE
    }

    /**
     * Characters that land on the same key in both layouts, so typing them never
     * requires the host to switch.
     */
    private val layoutNeutral = setOf(' ', '\n', '\r', '\t', '\b', '\u001B')

    /**
     * The host layout needed to type [c], or null if the character is either
     * layout-neutral or cannot be typed at all.
     */
    fun requiredLayout(c: Char): HostLayout? = when {
        c in layoutNeutral -> null
        ThaiKedmanee.canType(c) -> HostLayout.THAI
        charMap.containsKey(c) -> HostLayout.US
        else -> null
    }

    /** Key codes an IME may deliver through `InputConnection.sendKeyEvent`. */
    fun forAndroidKeyCode(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_DEL -> HidSpec.KEY_BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> HidSpec.KEY_DELETE
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> HidSpec.KEY_ENTER
        KeyEvent.KEYCODE_TAB -> HidSpec.KEY_TAB
        KeyEvent.KEYCODE_ESCAPE -> HidSpec.KEY_ESC
        KeyEvent.KEYCODE_SPACE -> HidSpec.KEY_SPACE
        KeyEvent.KEYCODE_DPAD_UP -> HidSpec.KEY_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> HidSpec.KEY_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> HidSpec.KEY_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> HidSpec.KEY_RIGHT
        KeyEvent.KEYCODE_MOVE_HOME -> HidSpec.KEY_HOME
        KeyEvent.KEYCODE_MOVE_END -> HidSpec.KEY_END
        KeyEvent.KEYCODE_PAGE_UP -> HidSpec.KEY_PAGE_UP
        KeyEvent.KEYCODE_PAGE_DOWN -> HidSpec.KEY_PAGE_DOWN
        KeyEvent.KEYCODE_INSERT -> HidSpec.KEY_INSERT
        else -> NONE
    }
}
