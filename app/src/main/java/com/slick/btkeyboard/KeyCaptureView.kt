package com.slick.btkeyboard

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * An invisible editor that captures everything the user's own IME produces.
 *
 * Soft keyboards do not emit key events for printable characters, they commit
 * text into an [InputConnection]. This view exposes a connection that has no
 * backing text buffer: every commit, composition change and deletion is
 * forwarded straight to [callback] instead of being stored, so whatever
 * keyboard app the user has installed becomes the input source.
 */
class KeyCaptureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Callback {
        /** A run of printable characters the user typed. */
        fun onText(text: CharSequence)

        /** A non-printable key, already resolved to a HID usage. */
        fun onKey(usage: Int)

        /** [count] backspaces, e.g. from the IME's delete key or a correction. */
        fun onBackspace(count: Int)
    }

    var callback: Callback? = null

    /** Text currently held in the IME's composing region, mirrored on the host. */
    private var composing: String = ""

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // VISIBLE_PASSWORD + NO_SUGGESTIONS stops most IMEs from autocorrecting
        // or composing, so characters arrive one at a time as they are tapped.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        return CaptureConnection()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            showKeyboard()
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    /** Hardware keyboards (and some IMEs) still deliver real key events. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKeyEvent(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        val usage = UsKeymap.forAndroidKeyCode(keyCode)
        if (usage != UsKeymap.NONE) {
            if (usage == HidSpec.KEY_BACKSPACE) {
                emitBackspace(1)
            } else {
                composing = ""
                callback?.onKey(usage)
            }
            return true
        }
        val unicode = event.unicodeChar
        if (unicode != 0) {
            emitText(unicode.toChar().toString())
            return true
        }
        return false
    }

    private fun emitText(text: CharSequence) {
        if (text.isEmpty()) return
        callback?.onText(text)
    }

    private fun emitBackspace(count: Int) {
        if (count <= 0) return
        callback?.onBackspace(count)
    }

    /**
     * An [InputConnection] with no editable buffer. Every method that would
     * normally mutate text instead streams the change to the host.
     */
    private inner class CaptureConnection : BaseInputConnection(this@KeyCaptureView, false) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val value = text?.toString() ?: return true
            // A commit replaces whatever is in the composing region, so undo the
            // characters we already mirrored before sending the final text.
            if (composing.isNotEmpty()) {
                emitBackspace(composing.length)
                composing = ""
            }
            emitText(value)
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val value = text?.toString() ?: ""
            val shared = commonPrefixLength(composing, value)
            emitBackspace(composing.length - shared)
            emitText(value.substring(shared))
            composing = value
            return true
        }

        override fun finishComposingText(): Boolean {
            composing = ""
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (composing.isNotEmpty()) {
                emitBackspace(composing.length)
                composing = ""
            }
            emitBackspace(beforeLength)
            if (afterLength > 0) {
                repeat(afterLength) { callback?.onKey(HidSpec.KEY_DELETE) }
            }
            return true
        }

        override fun deleteSurroundingTextInCodePoints(
            beforeLength: Int,
            afterLength: Int
        ): Boolean = deleteSurroundingText(beforeLength, afterLength)

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action != KeyEvent.ACTION_DOWN) return true
            return handleKeyEvent(event.keyCode, event)
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            composing = ""
            callback?.onKey(HidSpec.KEY_ENTER)
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean = true

        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence = ""

        override fun getTextAfterCursor(length: Int, flags: Int): CharSequence = ""

        override fun getSelectedText(flags: Int): CharSequence? = null
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }
}
