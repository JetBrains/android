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

import com.android.tools.idea.lang.AndroidParsingTestCase
import com.android.tools.idea.lang.proguard.grammar.ProguardLexer
import com.intellij.psi.TokenType

abstract class ProguardParserTest : AndroidParsingTestCase(
  ProguardFileType.INSTANCE.defaultExtension,
  ProguardParserDefinition()
) {
  override fun getTestDataPath() = com.android.tools.idea.lang.getTestDataPath()

  protected fun checkLex(input: String) {
    assert(getErrorMessage(input) == null, lazyMessage = { toParseTreeText(input) })

    val lexer = ProguardLexer()
    lexer.start(input)
    while (lexer.tokenType != null) {
      assert(lexer.tokenType != TokenType.BAD_CHARACTER) { "BAD_CHARACTER ${lexer.tokenText}" }
      lexer.advance()
    }
  }

  protected fun checkNotLex(input: String, expectedLexerError: String? = null) {
    try {
      checkLex(input)
    }
    catch (e: AssertionError) {
      if (expectedLexerError != null) {
        assert(getErrorMessage(input) == expectedLexerError)
      }
      return
    }
    fail()
  }

  private fun checkParse(input: String) {
    assert(getErrorMessage(input) == null)
  }

  private fun checkNotParse(input: String, expectedParserError: String? = null) {
    try {
      checkParse(input)
    }
    catch (e: AssertionError) {
      if (expectedParserError != null) {
        assert(getErrorMessage(input) == expectedParserError)
      }
      return
    }
    fail()
  }

  protected fun checkFilenameFlag(flag: String, mandatory: Boolean, spaceRequired: Boolean = true) {
    checkLex("${flag} general.pro")
    if (spaceRequired) {
      // TODO(b/72461769): The lexer is too lax for this to fail
      // checkNotLex("${flag}general.pro")
    }
    else {
      checkParse("${flag}general.pro")
    }
    checkParse("${flag} general.pro # with comment")
    checkParse("${flag} 'name with spaces.pro'")
    checkParse("${flag} \"name with spaces.pro\"")

    checkNotParse("${flag} one.pro two.pro",
                "ProguardTokenType.CRLF or ProguardTokenType.LINE_CMT expected, got 'two.pro'")
    if (mandatory) {
      // TODO(xof): this error message is misleading: it should not allow CRLF or LINE_CMT
      val expectedParserError = "<filename arg>, ProguardTokenType.CRLF or ProguardTokenType.LINE_CMT expected"
      checkNotParse("${flag}", expectedParserError)
      checkNotParse("${flag} ", expectedParserError)
      checkNotParse("${flag} # with comment", expectedParserError)
    }
    else {
      checkParse("${flag}")
      checkParse("${flag} ")
      checkParse("${flag} # with comment")
    }
    run {
      val expectedLexerErrorStem = when {
        // TODO(xof): again, this error message is misleading and shouldn't allow CRLF or LINE_CMT
        mandatory -> "<filename arg>, ProguardTokenType.CRLF or ProguardTokenType.LINE_CMT expected, got "
        else -> "<filename arg>, ProguardTokenType.CRLF or ProguardTokenType.LINE_CMT expected, got "
      }
      checkNotLex("${flag}'", "$expectedLexerErrorStem'''")
      checkNotLex("${flag} '", "$expectedLexerErrorStem'''")
      checkNotLex("${flag}\"", "$expectedLexerErrorStem'\"'")
      checkNotLex("${flag} \"", "$expectedLexerErrorStem'\"'")
      checkNotLex("${flag} ' # not a comment", "$expectedLexerErrorStem'' # not a comment'")
      checkNotLex("${flag} \" # not a comment", "$expectedLexerErrorStem'\" # not a comment'")
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
