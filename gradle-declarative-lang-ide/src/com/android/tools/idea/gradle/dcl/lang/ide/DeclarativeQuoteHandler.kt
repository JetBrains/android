/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.MULTILINE_STRING_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeTokenSets.STRING_LITERALS
import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager

class DeclarativeQuoteHandler : SimpleTokenSetQuoteHandler(STRING_LITERALS),
                                MultiCharQuoteHandler {
  override fun isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean {
    if (iterator.tokenType === MULTILINE_STRING_LITERAL) {
      val start = iterator.start
      val end = iterator.end
      return end - start >= 5 && offset >= end - 3
    }
    return super.isClosingQuote(iterator, offset)
  }

  override fun getClosingQuote(iterator: HighlighterIterator, offset: Int): CharSequence? {
    return if ((iterator.tokenType === MULTILINE_STRING_LITERAL)
               && offset == iterator.start + 3) "\"\"\""
    else null
  }

  override fun hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean {
    if (iterator.tokenType === MULTILINE_STRING_LITERAL) {
      val document = editor.document
      val text = document.text
      val hasOpenQuotes = StringUtil.equals(text.substring(iterator.start, offset + 1), "\"\"\"")
      if (hasOpenQuotes) {
        val hasCloseQuotes = StringUtil.contains(text.substring(offset + 1, iterator.end), "\"\"\"")
        if (!hasCloseQuotes) return true
        // check if parser interpreted next text block start quotes as end quotes for the current one
        val nTextBlockQuotes = StringUtil.getOccurrenceCount(text.substring(iterator.end), "\"\"\"")
        return nTextBlockQuotes % 2 != 0
      }
    }
    return super.hasNonClosedLiteral(editor, iterator, offset)
  }

  override fun insertClosingQuote(editor: Editor, offset: Int, file: PsiFile, closingQuote: CharSequence) {
    editor.document.insertString(offset, "\"\"\"")
    val project = file.project
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    val token = file.findElementAt(offset) ?: return
    val parent = token.parent ?: return
    if (parent.node.elementType == MULTILINE_STRING_LITERAL) {
      CodeStyleManager.getInstance(project).reformat(parent)
      editor.caretModel.moveToOffset(parent.textRange.endOffset - 3)
    }
  }
}

