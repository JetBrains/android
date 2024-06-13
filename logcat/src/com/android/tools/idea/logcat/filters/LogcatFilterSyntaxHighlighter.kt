/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters

import com.android.tools.idea.logcat.filters.parser.LogcatFilterLexerAdapter
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.VALUE
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.EMPTY_ARRAY
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

internal enum class LogcatFilterTextAttributes(fallback: TextAttributesKey? = null) {
  KEY,
  KVALUE,
  STRING_KVALUE,
  REGEX_KVALUE,
  VALUE(HighlighterColors.TEXT),
  BAD_CHARACTER(HighlighterColors.BAD_CHARACTER);

  val key = TextAttributesKey.createTextAttributesKey("LOGCAT_FILTER_$name", fallback)
  val keys = arrayOf(key)
}

/** A [com.intellij.openapi.fileTypes.SyntaxHighlighter] for the Logcat Filter language. */
internal class LogcatFilterSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = LogcatFilterLexerAdapter()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
    when (tokenType) {
      KEY,
      STRING_KEY,
      REGEX_KEY -> LogcatFilterTextAttributes.KEY.keys
      KVALUE -> LogcatFilterTextAttributes.KVALUE.keys
      STRING_KVALUE -> LogcatFilterTextAttributes.STRING_KVALUE.keys
      REGEX_KVALUE -> LogcatFilterTextAttributes.REGEX_KVALUE.keys
      VALUE -> LogcatFilterTextAttributes.VALUE.keys
      TokenType.BAD_CHARACTER -> LogcatFilterTextAttributes.BAD_CHARACTER.keys
      else -> EMPTY_ARRAY
    }
}

internal class LogcatFilterSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(
    project: Project?,
    virtualFile: VirtualFile?,
  ): SyntaxHighlighter = LogcatFilterSyntaxHighlighter()
}
