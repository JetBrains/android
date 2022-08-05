/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.lang.agsl

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.lang.AndroidParsingTestCase
import com.intellij.psi.TokenType
import org.intellij.lang.annotations.Language

/** Tests parsing for AGSL files */
class AgslParserTest : AndroidParsingTestCase("", AgslParserDefinition()) {

  override fun setUp() {
    super.setUp()
    StudioFlags.AGSL_LANGUAGE_SUPPORT.override(true)
  }

  override fun tearDown() {
    try {
      StudioFlags.AGSL_LANGUAGE_SUPPORT.clearOverride()
    }
    finally {
      super.tearDown()
    }
  }

  override fun getTestDataPath(): String = com.android.tools.idea.lang.getTestDataPath()

  override fun skipSpaces(): Boolean {
    return true
  }

  /**
   * Checks that the given text parses correctly.
   *
   * For now the PSI hierarchy is not finalized, so there's no point checking the tree shape.
   */
  private fun check(@Language("AGSL") input: String) {
    assert(getErrorMessage(input) == null, lazyMessage = { toParseTreeText(input.trimIndent()) })

    val lexer = AgslLexer()
    lexer.start(input)
    while (lexer.tokenType != null) {
      assert(lexer.tokenType != TokenType.BAD_CHARACTER) { "BAD_CHARACTER ${lexer.tokenText}" }
      lexer.advance()
    }
  }

  fun testLanguageOff() {
    StudioFlags.AGSL_LANGUAGE_SUPPORT.override(false)
    check("struct 123 1.0 abc")
  }

  fun testBasics() {
    check(
      """
      // Comment
      uniform shader imageA;
      uniform shader imageB;
      uniform ivec2 imageDimensions;
      uniform float progress;

      const vec2 iSize = vec2(48.0, 48.0);
      const float iDir = 0.5;
      const  float iRand = 0.81;

      float hash12(vec2 p) {
          vec3 p3  = fract(vec3(p.xyx) * .1031);
          p3 += dot(p3, p3.yzx + 33.33);
          return fract((p3.x + p3.y) * p3.z);
      }

      float ramp(float2 p) {
        return mix(hash12(p),
                   dot(p/vec2(imageDimensions), float2(iDir, 1 - iDir)),
                   iRand);
      }

      half4 main(float2 p) {
        float2 lowRes = p / iSize;
        float2 cellCenter = (floor(lowRes) + 0.5) * iSize;
        float2 posInCell = fract(lowRes) * 2 - 1;

        float v = ramp(cellCenter) + progress;
        float distToCenter = max(abs(posInCell.x), abs(posInCell.y));

        return distToCenter > v ? imageA.eval(p).rgb1 : imageB.eval(p).rgb1;
      }
      """
    )
  }

  fun testEmptyOk() {
    check("")
  }

  fun testInvalidCharacter() {
    try {
      check("\u0000")
      fail()
    }
    catch (e: AssertionError) {
      // Expected.
    }
  }

  fun testDiscardDisallowed() {
    assertEquals(
      """
      FILE
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.in)('in')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.vec3)('vec3')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.IDENTIFIER)('FrontColor')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.;)(';')
        AgslTokenImpl(TOKEN)
          AgslUnsupportedKeywordImpl(UNSUPPORTED_KEYWORD)
            PsiElement(AgslTokenType.discard)('discard')
      """.trimIndent(),

      toParseTreeText(
        //language=AGSL
        """
        in vec3 FrontColor;
        discard
        """.trimIndent()
      )
    )
  }

  fun testFutureKeyword() {
    assertEquals(
      """
      FILE
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.void)('void')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.IDENTIFIER)('main')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.()('(')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.))(')')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.{)('{')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.const)('const')
        AgslTokenImpl(TOKEN)
          AgslReservedKeywordImpl(RESERVED_KEYWORD)
            PsiElement(AgslTokenType.double)('double')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.IDENTIFIER)('scale')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.=)('=')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.FLOATCONSTANT)('2.0')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.;)(';')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.})('}')
      """.trimIndent(),

      toParseTreeText(
        //language=AGSL
        """
        void main() {
          const double scale = 2.0;
        }
        """.trimIndent()
      )
    )
  }

  fun testPreprocessor() {
    assertEquals(
      """
      FILE
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.int)('int')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.IDENTIFIER)('foo')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.=)('=')
        AgslTokenImpl(TOKEN)
          PsiElement(AgslTokenType.INTCONSTANT)('1')
        PsiErrorElement:<token> expected, got '#'
          PsiElement(BAD_CHARACTER)('#')
        PsiElement(AgslTokenType.IDENTIFIER)('define')
        PsiElement(AgslTokenType.IDENTIFIER)('FOO')
        PsiElement(AgslTokenType.INTCONSTANT)('2')
        PsiElement(AgslTokenType.int)('int')
        PsiElement(AgslTokenType.IDENTIFIER)('bar')
        PsiElement(AgslTokenType.=)('=')
        PsiElement(AgslTokenType.INTCONSTANT)('3')
      """.trimIndent(),

      toParseTreeText(
        //language=AGSL
        """
        int foo = 1
        #define FOO 2
        int bar = 3
        """.trimIndent()
      )
    )
  }
}