/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.adb.wireless

import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBFont
import java.awt.Graphics
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.BadLocationException
import javax.swing.text.DocumentFilter
import javax.swing.text.JTextComponent
import kotlin.streams.toList

/**
 * A specialized version of [JBTextField] that allows for entering only
 * a single digit and that does not display the caret when focused
 */
internal class JSingleDigitTextField : JBTextField() {
  init {
    // Set a big font
    font = JBFont.label().deriveFont(16f)

    // Set initial digit to '0' so we don't display an empty box
    text = "0"

    // Set document filter so that we can ensure only single digit contents
    (document as AbstractDocument).documentFilter = OneDigitOnlyDocumentFilter()
  }

  override fun paintComponent(g: Graphics) {
    // Ensure caret is never painted (we have do this before every paint, as the state
    // changes when the text field gets focus, etc.
    caret?.apply {
      isVisible = false
      isSelectionVisible = false
    }
    super.paintComponent(g)
  }

  /**
   * A document filter that ensures only digits (a maximum of 6) are entered
   * in the corresponding [JSingleDigitTextField]
   */
  private class OneDigitOnlyDocumentFilter : DocumentFilter() {
    @Throws(BadLocationException::class)
    override fun replace(fb: FilterBypass,
                         offset: Int,
                         length: Int,
                         text: String?,
                         attrs: AttributeSet?) {
      if (text == null) {
        // Deletion case
        super.replace(fb, offset, length, text, attrs)
      } else {
        // Insert or replace case: Filter out any non digit character
        val filteredInput = text.codePoints()
          .toList()
          .map { c -> c.toChar() }
          .filter { c -> c in '0'..'9' }
          .joinToString(separator = "") { it.toString() }
        if (filteredInput != text) {
          Toolkit.getDefaultToolkit().beep()
        }
        if (filteredInput.length == 1) {
          // Replace all document with the single digit text
          super.replace(fb, 0, fb.document.length, filteredInput, attrs)
          handleAutoTab(fb)
        }
        else {
          Toolkit.getDefaultToolkit().beep()
        }
      }
    }

    @Throws(BadLocationException::class)
    override fun insertString(fb: FilterBypass,
                              offs: Int,
                              text: String,
                              a: AttributeSet?) {
      replace(fb, offs, 0, text, a)
    }

    /**
     * Move focus to next component when a character has been entered
     */
    protected fun handleAutoTab(fb: FilterBypass) {
      val c = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
      if (c is JTextComponent) {
        val doc = c.document
        if (doc == fb.document) {
          c.transferFocus()
        }
      }
    }
  }
}