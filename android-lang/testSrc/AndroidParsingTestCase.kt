/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.idea.lang

import com.google.common.truth.Truth.assertThat
import com.intellij.lang.ParserDefinition
import com.intellij.lexer.FlexAdapter
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import junit.framework.TestCase

abstract class AndroidParsingTestCase(
  fileException: String,
  parserDefinition: ParserDefinition
) : ParsingTestCase("no_data_path_needed", fileException, parserDefinition) {

  override fun getTestDataPath() = com.android.tools.idea.lang.getTestDataPath()

  protected fun toParseTreeText(input: String): String {
    val psiFile = createPsiFile("in-memory", input)
    return toParseTreeText(psiFile, true, false).trim()
  }

  protected fun getErrorMessage(input: String): String? {
    val psiFile = createPsiFile("in-memory", input)
    return getErrorMessage(psiFile)
  }

  private fun getErrorMessage(psiFile: PsiFile?) = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement::class.java)?.errorDescription
}

abstract class AndroidLexerTestCase(private val lexer: FlexAdapter): TestCase() {

  protected val SPACE = " " to TokenType.WHITE_SPACE
  protected val NEWLINE = "\n" to TokenType.WHITE_SPACE

  protected fun assertTokenTypes(input: String, vararg expectedTokenTypes: Pair<String, IElementType>) {
    val actualTokenTypes = mutableListOf<Pair<String, IElementType>>()
    lexer.start(input)
    while (lexer.tokenType != null) {
      actualTokenTypes.add(lexer.tokenText to lexer.tokenType!!)
      lexer.advance()
    }

    assertThat(actualTokenTypes).containsExactlyElementsIn(expectedTokenTypes.asIterable())
  }
}
