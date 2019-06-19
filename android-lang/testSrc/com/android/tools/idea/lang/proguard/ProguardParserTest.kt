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
  protected fun checkNot(input: String, expectedLexerError: String? = null) {
    try {
      check(input)
    }
    catch (e: AssertionError) {
      if (expectedLexerError != null) {
        assert(getErrorMessage(input) == expectedLexerError)
      }
      return
    }
    fail()
  }

  protected fun checkFilenameFlag(flag: String, mandatory: Boolean, spaceRequired: Boolean = true) {
    check("${flag} general.pro")
    if (spaceRequired) {
      // TODO(b/72461769): The lexer is too lax for this to fail
      // checkNot("${flag}general.pro")
    }
    else {
      check("${flag}general.pro")
    }
    check("${flag} general.pro # with comment")
    check("${flag} 'name with spaces.pro'")
    check("${flag} \"name with spaces.pro\"")

    checkNot("${flag} one.pro two.pro",
             "ProguardTokenType.CRLF or ProguardTokenType.LINE_CMT expected, got 'two.pro'")
    if (mandatory) {
      val expectedLexerError = "ProguardTokenType.FILENAME_FLAG_ARG expected"
      checkNot("${flag}", expectedLexerError)
      checkNot("${flag} ", expectedLexerError)
      checkNot("${flag} # with comment", expectedLexerError)
    }
    else {
      check("${flag}")
      check("${flag} ")
      check("${flag} # with comment")
    }
    run {
      val expectedLexerErrorStem = when {
        mandatory -> "ProguardTokenType.FILENAME_FLAG_ARG expected, got "
        else -> "ProguardTokenType.CRLF, ProguardTokenType.FILENAME_FLAG_ARG or ProguardTokenType.LINE_CMT expected, got "
      }
      checkNot("${flag}'", "$expectedLexerErrorStem'''")
      checkNot("${flag} '", "$expectedLexerErrorStem'''")
      checkNot("${flag}\"", "$expectedLexerErrorStem'\"'")
      checkNot("${flag} \"", "$expectedLexerErrorStem'\"'")
      checkNot("${flag} ' # not a comment", "$expectedLexerErrorStem'''")
      checkNot("${flag} \" # not a comment", "$expectedLexerErrorStem'\"'")
    }
  }

}

class MiscParserTest : ProguardParserTest() {
  fun testCommercialAt() {
    checkFilenameFlag("@", true, spaceRequired = false)
  }

  fun testInclude() {
    checkFilenameFlag("-include", true)
  }

  fun testApplyMapping() {
    checkFilenameFlag("-applymapping", true)
  }

  fun testObfuscationDictionary() {
    checkFilenameFlag("-obfuscationdictionary", true)
  }

  fun testClassObfuscationDictionary() {
    checkFilenameFlag("-classobfuscationdictionary", true)
  }

  fun testPackageObfuscationDictionary() {
    checkFilenameFlag("-packageobfuscationdictionary", true)
  }

  fun testPrintSeeds() {
    checkFilenameFlag("-printseeds", false)
  }

  fun testPrintUsage() {
    checkFilenameFlag("-printusage", false)
  }

  fun testPrintMapping() {
    checkFilenameFlag("-printmapping", false)
  }

  fun testPrintConfiguration() {
    checkFilenameFlag("-printconfiguration", false)
  }

  fun testDump() {
    checkFilenameFlag("-dump", false)
  }
}
