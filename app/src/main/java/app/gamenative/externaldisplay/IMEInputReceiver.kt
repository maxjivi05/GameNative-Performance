package app.gamenative.externaldisplay

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.winlator.xserver.XServer
import timber.log.Timber

class IMEInputReceiver(
    context: Context,
    private val displayContext: Context,
    private val xServer: XServer,
) : View(context) {

    private var imeSessionActive = false

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onCheckIsTextEditor(): Boolean {
        val isEditor = imeSessionActive && hasFocus()
        Timber.d("IMEInputReceiver: onCheckIsTextEditor called - returning $isEditor")
        return isEditor
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        if (!imeSessionActive || !hasFocus()) {
            Timber.d(
                "IMEInputReceiver: onCreateInputConnection ignored (active=$imeSessionActive, hasFocus=${hasFocus()})",
            )
            return BaseInputConnection(this, false)
        }

        Timber.d("IMEInputReceiver: onCreateInputConnection called!")
        // Disable autocomplete/suggestions so each key commits immediately
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or
                EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE

        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                Timber.d("IMEInputReceiver: commitText: $text")
                text?.forEach { char ->
                    val keyCode = KeyDispatch.fromChar(char)
                    if (keyCode != null) {
                        dispatchKey(keyCode.keyCode, keyCode.requiresShift)
                    }
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                Timber.d("IMEInputReceiver: sendKeyEvent: ${event.keyCode}")
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val xKey = KeyDispatch.fromAndroidKeyCode(event.keyCode)
                    if (xKey != null) {
                        dispatchKey(xKey, false)
                    }
                }
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    dispatchKey(KeyEvent.KEYCODE_DEL, false)
                }
                return true
            }
        }
    }

    private fun dispatchKey(androidKeyCode: Int, requiresShift: Boolean) {
        val keyboard = xServer.keyboard
        if (requiresShift) keyboard.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT))
        keyboard.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, androidKeyCode))
        keyboard.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, androidKeyCode))
        if (requiresShift) keyboard.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT))
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        Timber.d("IMEInputReceiver: onWindowFocusChanged: $hasWindowFocus")
        if (hasWindowFocus && imeSessionActive && !hasFocus()) {
            post { requestFocus() }
        }
    }

    private data class KeyDispatch(
        val keyCode: Int,
        val requiresShift: Boolean,
    ) {
        companion object {
            fun fromChar(char: Char): KeyDispatch? {
                return when (char) {
                    in 'a'..'z' -> KeyDispatch(KeyEvent.KEYCODE_A + (char - 'a'), false)
                    in 'A'..'Z' -> KeyDispatch(KeyEvent.KEYCODE_A + (char - 'A'), true)
                    in '0'..'9' -> KeyDispatch(KeyEvent.KEYCODE_0 + (char - '0'), false)
                    ' ' -> KeyDispatch(KeyEvent.KEYCODE_SPACE, false)
                    '\n' -> KeyDispatch(KeyEvent.KEYCODE_ENTER, false)
                    '.' -> KeyDispatch(KeyEvent.KEYCODE_PERIOD, false)
                    ',' -> KeyDispatch(KeyEvent.KEYCODE_COMMA, false)
                    '/' -> KeyDispatch(KeyEvent.KEYCODE_SLASH, false)
                    '\\' -> KeyDispatch(KeyEvent.KEYCODE_BACKSLASH, false)
                    ';' -> KeyDispatch(KeyEvent.KEYCODE_SEMICOLON, false)
                    '\'' -> KeyDispatch(KeyEvent.KEYCODE_APOSTROPHE, false)
                    '[' -> KeyDispatch(KeyEvent.KEYCODE_LEFT_BRACKET, false)
                    ']' -> KeyDispatch(KeyEvent.KEYCODE_RIGHT_BRACKET, false)
                    '-' -> KeyDispatch(KeyEvent.KEYCODE_MINUS, false)
                    '=' -> KeyDispatch(KeyEvent.KEYCODE_EQUALS, false)
                    '`' -> KeyDispatch(KeyEvent.KEYCODE_GRAVE, false)
                    '!' -> KeyDispatch(KeyEvent.KEYCODE_1, true)
                    '@' -> KeyDispatch(KeyEvent.KEYCODE_2, true)
                    '#' -> KeyDispatch(KeyEvent.KEYCODE_3, true)
                    '$' -> KeyDispatch(KeyEvent.KEYCODE_4, true)
                    '%' -> KeyDispatch(KeyEvent.KEYCODE_5, true)
                    '^' -> KeyDispatch(KeyEvent.KEYCODE_6, true)
                    '&' -> KeyDispatch(KeyEvent.KEYCODE_7, true)
                    '*' -> KeyDispatch(KeyEvent.KEYCODE_8, true)
                    '(' -> KeyDispatch(KeyEvent.KEYCODE_9, true)
                    ')' -> KeyDispatch(KeyEvent.KEYCODE_0, true)
                    '_' -> KeyDispatch(KeyEvent.KEYCODE_MINUS, true)
                    '+' -> KeyDispatch(KeyEvent.KEYCODE_EQUALS, true)
                    '{' -> KeyDispatch(KeyEvent.KEYCODE_LEFT_BRACKET, true)
                    '}' -> KeyDispatch(KeyEvent.KEYCODE_RIGHT_BRACKET, true)
                    '|' -> KeyDispatch(KeyEvent.KEYCODE_BACKSLASH, true)
                    ':' -> KeyDispatch(KeyEvent.KEYCODE_SEMICOLON, true)
                    '"' -> KeyDispatch(KeyEvent.KEYCODE_APOSTROPHE, true)
                    '<' -> KeyDispatch(KeyEvent.KEYCODE_COMMA, true)
                    '>' -> KeyDispatch(KeyEvent.KEYCODE_PERIOD, true)
                    '?' -> KeyDispatch(KeyEvent.KEYCODE_SLASH, true)
                    '~' -> KeyDispatch(KeyEvent.KEYCODE_GRAVE, true)
                    else -> null
                }
            }

            fun fromAndroidKeyCode(keyCode: Int): Int? {
                return when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> KeyEvent.KEYCODE_DEL
                    KeyEvent.KEYCODE_ENTER -> KeyEvent.KEYCODE_ENTER
                    KeyEvent.KEYCODE_TAB -> KeyEvent.KEYCODE_TAB
                    KeyEvent.KEYCODE_ESCAPE -> KeyEvent.KEYCODE_ESCAPE
                    else -> null
                }
            }
        }
    }

    fun showKeyboard() {
        post {
            imeSessionActive = true
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            val imm = displayContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            Timber.d("IMEInputReceiver: Requested to show keyboard")
        }
    }

    fun hideKeyboard() {
        post {
            val imm = displayContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(windowToken, 0)
            clearFocus()
            imeSessionActive = false
            isFocusable = false
            isFocusableInTouchMode = false
            Timber.d("IMEInputReceiver: Requested to hide keyboard")
        }
    }
}
