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
package com.android.tools.idea.gradle.dcl.lang.parser

import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.OP_LPAREN
import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType

object DeclarativeParserUtil: GeneratedParserUtilBase() {
  @JvmStatic
  fun atSameLine(b: PsiBuilder, level: Int, parser: Parser): Boolean {
    val marker = enter_section_(b)
    b.eof() // skip whitespace
    val isSameLine = !isNextAfterNewLine(b)
    val result = isSameLine && parser.parse(b, level)
    exit_section_(b, marker, null, result)
    return result
  }

  @JvmStatic
  fun atNewLine(b: PsiBuilder, level: Int, parser: Parser): Boolean {
    val marker = enter_section_(b)
    b.eof() // skip whitespace
    val result = isNextAfterNewLine(b) && parser.parse(b, level)
    exit_section_(b, marker, null, result)
    return result
  }

  @JvmStatic
  fun notBeforeLParen(b: PsiBuilder, level: Int, parser: Parser): Boolean {
    val marker = enter_section_(b)
    b.eof() // skip whitespace
    val result = parser.parse(b, level) && !isBefore(b, OP_LPAREN)
    exit_section_(b, marker, null, result)
    return result
  }
}

private fun isBefore(b: PsiBuilder, element: IElementType): Boolean =
  b.rawLookup(0) == element

private fun isNextAfterNewLine(b: PsiBuilder): Boolean {
  return when (b.rawLookup(-1)) {
    null -> true // first element
    // The previous white space token contains end of line, or it's the first white space in file
    WHITE_SPACE -> b.rawLookupText(-1).contains("\n") || b.rawTokenIndex() == 1
    else -> false
  }
}

private fun PsiBuilder.rawLookupText(steps: Int): CharSequence {
  val start = rawTokenTypeStart(steps)
  val end = rawTokenTypeStart(steps + 1)
  return if (start == -1 || end == -1) "" else originalText.subSequence(start, end)
}
