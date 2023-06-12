/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.gmdcodecompletion.completions

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext

/**
 * Add custom behavior after code completion insertion. Mainly used to add double quotation marks to string values
 */
class GmdDevicePropertyInsertHandler : InsertHandler<GmdCodeCompletionLookupElement> {

  // Additional processing after user selects item from code completion suggestion list
  override fun handleInsert(context: InsertionContext, gmdPropertyElement: GmdCodeCompletionLookupElement) {
    val editor = context.editor
    val document = editor.document
    context.commitDocument()
    // Check and add double quotation marks when they are not present
    val hasLeftQuote = hasDoubleQuotation(context, true)
    val hasRightQuote = hasDoubleQuotation(context, false)
    if (!hasLeftQuote) {
      document.insertString(context.startOffset, "\"")
    }
    if (!hasRightQuote) {
      document.insertString(context.tailOffset, "\"")
    }
    // Move caret right after inserting to make sure caret is not inside code completion items
    editor.caretModel.moveCaretRelatively(1, 0, false, false, true)
  }

  // Returns true if there's a double quotation mark on the left / right the item
  private fun hasDoubleQuotation(context: InsertionContext, isLeft: Boolean): Boolean {
    val file = context.file
    if (file.findElementAt(context.startOffset)?.textContains('\"') == true) return true
    // Element at (context.startOffset - 1) is the start of the word. Left double quotation mark should exist here.
    val index = if (isLeft) (context.startOffset - 1) else context.tailOffset
    return (file.findElementAt(index)?.textContains('\"') == true)
  }
}