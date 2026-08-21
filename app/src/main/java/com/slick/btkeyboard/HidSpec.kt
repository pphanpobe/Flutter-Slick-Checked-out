package com.slick.btkeyboard

/**
 * USB HID keyboard constants shared by the report builder and the key maps.
 *
 * The report we emit is the classic 8 byte boot-keyboard report:
 *   [0] modifier bitmask
 *   [1] reserved (always 0)
 *   [2..7] up to six simultaneously pressed key usages
 */
object HidSpec {

    /** Report id used in the SDP descriptor below. */
    const val REPORT_ID_KEYBOARD = 8

    const val REPORT_SIZE = 8

    // Modifier bitmask values for byte 0 of the report.
    const val MOD_LEFT_CTRL = 0x01
    const val MOD_LEFT_SHIFT = 0x02
    const val MOD_LEFT_ALT = 0x04
    const val MOD_LEFT_GUI = 0x08
    const val MOD_RIGHT_CTRL = 0x10
    const val MOD_RIGHT_SHIFT = 0x20
    const val MOD_RIGHT_ALT = 0x40
    const val MOD_RIGHT_GUI = 0x80

    // Usage codes (HID Usage Page 0x07, "Keyboard/Keypad") for the keys the UI
    // exposes directly. Printable characters are resolved through UsKeymap.
    const val KEY_ENTER = 0x28
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_CAPS_LOCK = 0x39

    const val KEY_F1 = 0x3A // F1..F12 are contiguous: 0x3A .. 0x45

    const val KEY_PRINT_SCREEN = 0x46
    const val KEY_SCROLL_LOCK = 0x47
    const val KEY_PAUSE = 0x48
    const val KEY_INSERT = 0x49
    const val KEY_HOME = 0x4A
    const val KEY_PAGE_UP = 0x4B
    const val KEY_DELETE = 0x4C
    const val KEY_END = 0x4D
    const val KEY_PAGE_DOWN = 0x4E
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52

    const val KEY_APPLICATION = 0x65 // "menu" key

    /**
     * Boot-protocol compatible keyboard report descriptor.
     *
     * Logical/usage maximum is 0x65, which covers every key this app can send
     * and keeps the descriptor byte-for-byte compatible with the boot keyboard
     * layout that picky BIOS/UEFI hosts expect.
     */
    val REPORT_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), REPORT_ID_KEYBOARD.toByte(), //   Report ID (8)

        // --- 8 modifier bits ---
        0x05.toByte(), 0x07.toByte(), //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(), //   Usage Minimum (Left Control)
        0x29.toByte(), 0xE7.toByte(), //   Usage Maximum (Right GUI)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), //   Report Size (1)
        0x95.toByte(), 0x08.toByte(), //   Report Count (8)
        0x81.toByte(), 0x02.toByte(), //   Input (Data, Variable, Absolute)

        // --- reserved byte ---
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x75.toByte(), 0x08.toByte(), //   Report Size (8)
        0x81.toByte(), 0x01.toByte(), //   Input (Constant)

        // --- LED output report (host -> device), keeps hosts happy ---
        0x95.toByte(), 0x05.toByte(), //   Report Count (5)
        0x75.toByte(), 0x01.toByte(), //   Report Size (1)
        0x05.toByte(), 0x08.toByte(), //   Usage Page (LEDs)
        0x19.toByte(), 0x01.toByte(), //   Usage Minimum (Num Lock)
        0x29.toByte(), 0x05.toByte(), //   Usage Maximum (Kana)
        0x91.toByte(), 0x02.toByte(), //   Output (Data, Variable, Absolute)
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x75.toByte(), 0x03.toByte(), //   Report Size (3)
        0x91.toByte(), 0x01.toByte(), //   Output (Constant) - padding

        // --- six key slots ---
        0x95.toByte(), 0x06.toByte(), //   Report Count (6)
        0x75.toByte(), 0x08.toByte(), //   Report Size (8)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(), //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(), //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(), //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(), //   Input (Data, Array)

        0xC0.toByte()                 // End Collection
    )
}
