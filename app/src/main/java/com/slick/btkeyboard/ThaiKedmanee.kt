package com.slick.btkeyboard

/**
 * Thai Kedmanee (เกษมณี) layout table.
 *
 * A Bluetooth HID keyboard sends key *positions*, not characters, so the host
 * decides which glyph appears. When the host is switched to the Thai layout,
 * pressing the physical key that would type `q` on a US layout produces `ๆ`.
 *
 * This table inverts that relationship: given a Thai character, it returns the
 * US character sitting on the same physical key, which [UsKeymap] then resolves
 * to a usage plus shift state.
 *
 * Note this only works while the host itself is switched to Thai input. ASCII
 * characters are always mapped through the US table, so mixed Thai/English text
 * still needs a layout switch on the host side.
 */
object ThaiKedmanee {

    private val table: Map<Char, Char> = buildMap {
        // Number row (unshifted)
        put('ๅ', '1'); put('ภ', '4'); put('ถ', '5'); put('ุ', '6')
        put('ึ', '7'); put('ค', '8'); put('ต', '9'); put('จ', '0')
        put('ข', '-'); put('ช', '=')

        // Number row (shifted)
        put('๑', '@'); put('๒', '#'); put('๓', '$'); put('๔', '%')
        put('ู', '^'); put('฿', '&'); put('๕', '*'); put('๖', '(')
        put('๗', ')'); put('๘', '_'); put('๙', '+')

        // Top letter row (unshifted)
        put('ๆ', 'q'); put('ไ', 'w'); put('ำ', 'e'); put('พ', 'r'); put('ะ', 't')
        put('ั', 'y'); put('ี', 'u'); put('ร', 'i'); put('น', 'o'); put('ย', 'p')
        put('บ', '['); put('ล', ']'); put('ฃ', '\\')

        // Top letter row (shifted)
        put('๐', 'Q'); put('ฎ', 'E'); put('ฑ', 'R'); put('ธ', 'T')
        put('ํ', 'Y'); put('๊', 'U'); put('ณ', 'I'); put('ฯ', 'O'); put('ญ', 'P')
        put('ฐ', '{'); put('ฅ', '|')

        // Home row (unshifted)
        put('ฟ', 'a'); put('ห', 's'); put('ก', 'd'); put('ด', 'f'); put('เ', 'g')
        put('้', 'h'); put('่', 'j'); put('า', 'k'); put('ส', 'l')
        put('ว', ';'); put('ง', '\'')

        // Home row (shifted)
        put('ฤ', 'A'); put('ฆ', 'S'); put('ฏ', 'D'); put('โ', 'F'); put('ฌ', 'G')
        put('็', 'H'); put('๋', 'J'); put('ษ', 'K'); put('ศ', 'L'); put('ซ', ':')

        // Bottom row (unshifted)
        put('ผ', 'z'); put('ป', 'x'); put('แ', 'c'); put('อ', 'v'); put('ิ', 'b')
        put('ื', 'n'); put('ท', 'm'); put('ม', ','); put('ใ', '.'); put('ฝ', '/')

        // Bottom row (shifted)
        put('ฉ', 'C'); put('ฮ', 'V'); put('ฺ', 'B'); put('์', 'N')
        put('ฒ', '<'); put('ฬ', '>'); put('ฦ', '?')
    }

    fun toUsChar(c: Char): Char? = table[c]

    fun canType(c: Char): Boolean = table.containsKey(c)
}
