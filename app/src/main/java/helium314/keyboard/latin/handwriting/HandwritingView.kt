// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.handwriting

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import android.view.inputmethod.EditorInfo
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.event.HapticEvent

class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), KeyboardActionListener {

    private lateinit var languageLabel: TextView
    private lateinit var clearButton: ImageButton
    private lateinit var canvas: HandwritingCanvas
    private lateinit var bottomRowKeyboard: MainKeyboardView

    private var keyboardActionListener: KeyboardActionListener? = null
    private var editorInfo: EditorInfo? = null
    private var currentLanguage: String = ""

    private var currentComposingText = ""

    override fun onFinishInflate() {
        super.onFinishInflate()
        languageLabel = findViewById(R.id.handwriting_language_label)
        clearButton = findViewById(R.id.handwriting_clear_button)
        canvas = findViewById(R.id.handwriting_canvas)
        bottomRowKeyboard = findViewById(R.id.handwriting_bottom_row_keyboard)

        clearButton.setOnClickListener {
            clearCanvasAndComposition()
        }

        canvas.onStrokeStarted = {
            commitCurrentComposition()
            canvas.clear()
        }

        canvas.onRecognitionTriggered = { strokes ->
            performRecognition(strokes)
        }
    }

    fun startHandwriting(
        editorInfo: EditorInfo,
        keyboardActionListener: KeyboardActionListener,
        language: String
    ) {
        this.editorInfo = editorInfo
        this.keyboardActionListener = keyboardActionListener
        this.currentLanguage = language

        languageLabel.text = language

        // Setup bottom row keyboard
        bottomRowKeyboard.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
        bottomRowKeyboard.setKeyboardActionListener(this)

        try {
            PointerTracker.switchTo(bottomRowKeyboard)
            val kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(context, editorInfo)
            val keyboard = kls.getKeyboard(KeyboardId.ELEMENT_EMOJI_BOTTOM_ROW)
            bottomRowKeyboard.setKeyboard(keyboard)
        } catch (e: Exception) {
            Log.e("HandwritingView", "Failed to setup bottom row keyboard", e)
        }

        clearCanvasAndComposition()
    }

    fun stopHandwriting() {
        commitCurrentComposition()
        canvas.clear()
        bottomRowKeyboard.closing()
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (enabled) {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
    }

    fun commitCurrentComposition() {
        if (currentComposingText.isNotEmpty()) {
            val latinIME = KeyboardSwitcher.getInstance().latinIME ?: return
            val ic = latinIME.currentInputConnection ?: return
            ic.finishComposingText()
            currentComposingText = ""
            latinIME.setSuggestions(SuggestedWords.getEmptyInstance())
        }
    }

    fun clearCanvasAndComposition() {
        canvas.clear()
        currentComposingText = ""
        val latinIME = KeyboardSwitcher.getInstance().latinIME
        if (latinIME != null) {
            val ic = latinIME.currentInputConnection
            if (ic != null) {
                ic.finishComposingText()
            }
            latinIME.setSuggestions(SuggestedWords.getEmptyInstance())
        }
    }

    private fun performRecognition(strokes: List<FloatArray>) {
        if (strokes.isEmpty()) return
        val recognizer = HandwritingLoader.getRecognizer(context) ?: return
        
        // Ensure language is set
        recognizer.setLanguage(currentLanguage)
        
        val results = recognizer.recognize(strokes)
        if (results.isNullOrEmpty()) return

        val mainCandidate = results[0]
        currentComposingText = mainCandidate

        val latinIME = KeyboardSwitcher.getInstance().latinIME ?: return
        val ic = latinIME.currentInputConnection ?: return
        
        // Update composing text
        ic.setComposingText(mainCandidate, 1)

        // Populate suggestion strip with alternative candidates
        val suggestionInfos = ArrayList<SuggestedWordInfo>()
        for (word in results) {
            suggestionInfos.add(
                SuggestedWordInfo(
                    word,
                    "",
                    SuggestedWordInfo.MAX_SCORE,
                    SuggestedWordInfo.KIND_TYPED,
                    Dictionary.DICTIONARY_USER_TYPED,
                    SuggestedWordInfo.NOT_AN_INDEX,
                    SuggestedWordInfo.NOT_A_CONFIDENCE
                )
            )
        }

        val typedWordInfo = SuggestedWordInfo(
            mainCandidate,
            "",
            SuggestedWordInfo.MAX_SCORE,
            SuggestedWordInfo.KIND_TYPED,
            Dictionary.DICTIONARY_USER_TYPED,
            SuggestedWordInfo.NOT_AN_INDEX,
            SuggestedWordInfo.NOT_A_CONFIDENCE
        )

        val suggestedWords = SuggestedWords(
            suggestionInfos,
            null,
            typedWordInfo,
            false,
            false,
            false,
            SuggestedWords.INPUT_STYLE_TYPING,
            SuggestedWords.NOT_A_SEQUENCE_NUMBER
        )
        latinIME.setSuggestions(suggestedWords)
    }

    // Intercept KeyboardActionListener events for the bottom row
    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        if (primaryCode == KeyCode.ALPHA) {
            // Close handwriting mode
            KeyboardSwitcher.getInstance().setAlphabetKeyboard()
            return
        }
        
        // For other keys, commit the composition first when relevant
        if (primaryCode == Constants.CODE_SPACE || primaryCode == Constants.CODE_ENTER) {
            commitCurrentComposition()
        }
        
        keyboardActionListener?.onCodeInput(primaryCode, x, y, isKeyRepeat)
    }

    override fun onTextInput(text: String) {
        commitCurrentComposition()
        keyboardActionListener?.onTextInput(text)
    }

    override fun onImageSelected(imageUri: String?) {
        keyboardActionListener?.onImageSelected(imageUri)
    }

    override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean, hapticEvent: HapticEvent?) {
        keyboardActionListener?.onPressKey(primaryCode, repeatCount, isSinglePointer, hapticEvent)
    }

    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        keyboardActionListener?.onReleaseKey(primaryCode, withSliding)
    }

    override fun onLongPressKey(primaryCode: Int) {
        keyboardActionListener?.onLongPressKey(primaryCode)
    }

    override fun onKeyDown(keyCode: Int, keyEvent: android.view.KeyEvent?): Boolean {
        return keyboardActionListener?.onKeyDown(keyCode, keyEvent) ?: false
    }

    override fun onKeyUp(keyCode: Int, keyEvent: android.view.KeyEvent?): Boolean {
        return keyboardActionListener?.onKeyUp(keyCode, keyEvent) ?: false
    }

    override fun onStartBatchInput() { keyboardActionListener?.onStartBatchInput() }
    override fun onUpdateBatchInput(p: helium314.keyboard.latin.common.InputPointers?) { keyboardActionListener?.onUpdateBatchInput(p) }
    override fun onEndBatchInput(p: helium314.keyboard.latin.common.InputPointers?) { keyboardActionListener?.onEndBatchInput(p) }
    override fun onCancelBatchInput() { keyboardActionListener?.onCancelBatchInput() }
    override fun onCancelInput() { keyboardActionListener?.onCancelInput() }
    override fun onFinishSlidingInput() { keyboardActionListener?.onFinishSlidingInput() }
    override fun onCustomRequest(requestCode: Int): Boolean { return keyboardActionListener?.onCustomRequest(requestCode) ?: false }
    override fun onHorizontalSpaceSwipe(steps: Int): Boolean { return keyboardActionListener?.onHorizontalSpaceSwipe(steps) ?: false }
    override fun onVerticalSpaceSwipe(steps: Int): Boolean { return keyboardActionListener?.onVerticalSpaceSwipe(steps) ?: false }
    override fun onEndSpaceSwipe() { keyboardActionListener?.onEndSpaceSwipe() }
    override fun toggleNumpad(w: Boolean, f: Boolean): Boolean { return keyboardActionListener?.toggleNumpad(w, f) ?: false }
    override fun onMoveDeletePointer(steps: Int) { keyboardActionListener?.onMoveDeletePointer(steps) }
    override fun onUpWithDeletePointerActive() { keyboardActionListener?.onUpWithDeletePointerActive() }
    override fun resetMetaState() { keyboardActionListener?.resetMetaState() }
}
