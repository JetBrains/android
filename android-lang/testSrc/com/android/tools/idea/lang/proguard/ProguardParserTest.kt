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
package com.android.tools.idea.lang.proguard

import com.android.tools.idea.lang.proguard.grammar.ProguardLexer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase

abstract class ProguardParserTest : ParsingTestCase("no_data_path_needed",
                                                    ProguardFileType.INSTANCE.defaultExtension,
                                                    ProguardParserDefinition()
) {
  override fun getTestDataPath() = com.android.tools.idea.lang.getTestDataPath()

  // TODO(xof): duplicate code with our other parser tests (e.g. RoomSqlParserTest)
  protected fun check(input: String) {
    assert(getErrorMessage(input) == null, lazyMessage = { toParseTreeText(input) })

    val lexer = ProguardLexer()
    lexer.start(input)
    while (lexer.tokenType != null) {
      assert(lexer.tokenType != TokenType.BAD_CHARACTER) { "BAD_CHARACTER ${lexer.tokenText}" }
      lexer.advance()
    }
  }

  protected fun toParseTreeText(input: String): String {
    val psiFile = createPsiFile("in-memory", input)
    return toParseTreeText(psiFile, true, false).trim()
  }

  private fun getErrorMessage(input: String): String? {
    val psiFile = createPsiFile("in-memory", input)
    return getErrorMessage(psiFile)
  }

  private fun getErrorMessage(psiFile: PsiFile?) = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement::class.java)?.errorDescription

  // TODO(xof): this one isn't duplicate but probably it should be available
  protected fun checkNot(input: String) {
    try {
      check(input)
    } catch (e: AssertionError) {
      // expected
      return
    }
    fail()
  }
}

class MiscParserTest : ProguardParserTest() {
  fun testCommercialAt() {
    check("@ general.pro")
    check("@general.pro")
    check("@ general.pro # with comment")
    check("@ 'name with spaces.pro'")
    check("@ \"name with spaces.pro\"")

    checkNot("@ one.pro two.pro")
    checkNot("@")
    checkNot("@'")
    checkNot("@ \"")
    checkNot("@ ' # not a comment")
    checkNot("@\" # not a comment")
  }
}
