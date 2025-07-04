/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.lang.AndroidParsingTestCase

class WFFExpressionParserTest :
  AndroidParsingTestCase(WFFExpressionFileType.defaultExtension, WFFExpressionParserDefinition()) {

  override fun getTestDataPath() =
    resolveWorkspacePath("tools/adt/idea/wear-dwf/testData").toString()

  fun testParse() {
    assertEquals(
      """
FILE
  WFFExpressionConditionalExprImpl(CONDITIONAL_EXPR)
    WFFExpressionParenExprImpl(PAREN_EXPR)
      PsiElement(()('(')
      WFFExpressionConditionalExprImpl(CONDITIONAL_EXPR)
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          WFFExpressionConfigurationImpl(CONFIGURATION)
            PsiElement([)('[')
            WFFExpressionConfigurationIdImpl(CONFIGURATION_ID)
              PsiElement(ID)('CONFIGURATION')
              PsiElement(.)('.')
              WFFExpressionUserStringImpl(USER_STRING)
                PsiElement(ID)('showBackgroundInAfternoon')
            PsiElement(])(']')
        WFFExpressionConditionalOpImpl(CONDITIONAL_OP)
          PsiElement(OPERATORS)('==')
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          PsiElement(QUOTED_STRING)('"TRUE"')
      PsiElement())(')')
    WFFExpressionConditionalOpImpl(CONDITIONAL_OP)
      PsiElement(OPERATORS)('&&')
    WFFExpressionParenExprImpl(PAREN_EXPR)
      PsiElement(()('(')
      WFFExpressionConditionalExprImpl(CONDITIONAL_EXPR)
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          WFFExpressionDataSourceImpl(DATA_SOURCE)
            PsiElement([)('[')
            PsiElement(ID)('SECONDS_IN_DAY')
            PsiElement(])(']')
        WFFExpressionConditionalOpImpl(CONDITIONAL_OP)
          PsiElement(OPERATORS)('<')
        WFFExpressionCallExprImpl(CALL_EXPR)
          WFFExpressionFunctionIdImpl(FUNCTION_ID)
            PsiElement(ID)('log10')
          WFFExpressionArgListImpl(ARG_LIST)
            PsiElement(()('(')
            WFFExpressionLiteralExprImpl(LITERAL_EXPR)
              WFFExpressionNumberImpl(NUMBER)
                PsiElement(INTEGER)('10')
            PsiElement(,)(',')
            WFFExpressionLiteralExprImpl(LITERAL_EXPR)
              WFFExpressionNumberImpl(NUMBER)
                PsiElement(INTEGER)('2')
            PsiElement(,)(',')
            WFFExpressionLiteralExprImpl(LITERAL_EXPR)
              WFFExpressionNumberImpl(NUMBER)
                PsiElement(INTEGER)('3')
            PsiElement())(')')
      PsiElement())(')')
          """
        .trimIndent(),
      toParseTreeText(
        "([CONFIGURATION.showBackgroundInAfternoon] == \"TRUE\") && ([SECONDS_IN_DAY] < log10(10, 2, 3))"
      ),
    )
  }

  fun testParseNumber() {
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionNumberImpl(NUMBER)
      PsiElement(INTEGER)('1')
          """
        .trimIndent(),
      toParseTreeText("1"),
    )

    // we expect an integer after the dot
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionNumberImpl(NUMBER)
      PsiElement(INTEGER)('1')
  PsiElement(.)('.')
  PsiErrorElement:INTEGER expected
    <empty list>
          """
        .trimIndent(),
      toParseTreeText("1."),
    )

    // this is a valid number
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionNumberImpl(NUMBER)
      PsiElement(INTEGER)('1')
      PsiElement(.)('.')
      PsiElement(INTEGER)('2')
          """
        .trimIndent(),
      toParseTreeText("1.2"),
    )

    // this is not a valid number
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionNumberImpl(NUMBER)
      PsiElement(INTEGER)('1')
      PsiElement(.)('.')
      PsiElement(INTEGER)('2')
  PsiErrorElement:<conditional op> expected, got '.'
    PsiElement(.)('.')
  PsiElement(INTEGER)('3')
          """
        .trimIndent(),
      toParseTreeText("1.2.3"),
    )
  }

  fun testParseConfiguration() {
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionConfigurationImpl(CONFIGURATION)
      PsiElement([)('[')
      WFFExpressionConfigurationIdImpl(CONFIGURATION_ID)
        PsiElement(ID)('CONFIGURATION')
        PsiElement(.)('.')
        WFFExpressionUserStringImpl(USER_STRING)
          PsiElement(ID)('themeColor')
      PsiElement(])(']')
          """
        .trimIndent(),
      toParseTreeText("[CONFIGURATION.themeColor]"),
    )

    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionConfigurationImpl(CONFIGURATION)
      PsiElement([)('[')
      WFFExpressionConfigurationIdImpl(CONFIGURATION_ID)
        PsiElement(ID)('CONFIGURATION')
        PsiElement(.)('.')
        WFFExpressionUserStringImpl(USER_STRING)
          PsiElement(ID)('themeColor')
        WFFExpressionColorIndexImpl(COLOR_INDEX)
          PsiElement(.)('.')
          PsiElement(INTEGER)('1')
      PsiElement(])(']')
          """
        .trimIndent(),
      toParseTreeText("[CONFIGURATION.themeColor.1]"),
    )

    // An incomplete configuration should be considered as a configuration
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionConfigurationImpl(CONFIGURATION)
      PsiElement([)('[')
      WFFExpressionConfigurationIdImpl(CONFIGURATION_ID)
        PsiElement(ID)('CONFIGURATION')
        PsiElement(.)('.')
        PsiErrorElement:<user string> expected
          <empty list>
          """
        .trimIndent(),
      // missing everything after the configuration prefix
      toParseTreeText("[CONFIGURATION."),
    )

    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionConfigurationImpl(CONFIGURATION)
      PsiElement([)('[')
      WFFExpressionConfigurationIdImpl(CONFIGURATION_ID)
        PsiElement(ID)('CONFIGURATION')
        PsiElement(.)('.')
        WFFExpressionUserStringImpl(USER_STRING)
          PsiElement(ID)('themeColor')
        WFFExpressionColorIndexImpl(COLOR_INDEX)
          PsiElement(.)('.')
          PsiElement(INTEGER)('1')
      PsiErrorElement:ID or ']' expected
        <empty list>
          """
        .trimIndent(),
      // missing closing bracket
      toParseTreeText("[CONFIGURATION.themeColor.1"),
    )
  }

  fun testConfigurationsCanStartWithAnInteger() {
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionConfigurationImpl(CONFIGURATION)
      PsiElement([)('[')
      WFFExpressionConfigurationIdImpl(CONFIGURATION_ID)
        PsiElement(ID)('CONFIGURATION')
        PsiElement(.)('.')
        WFFExpressionUserStringImpl(USER_STRING)
          PsiElement(INTEGER)('40')
          PsiElement(ID)('fc6b01_0756_400d_8903_20a8808c8115')
      PsiElement(])(']')
          """
        .trimIndent(),
      // missing closing bracket
      toParseTreeText("[CONFIGURATION.40fc6b01_0756_400d_8903_20a8808c8115]"),
    )
  }

  fun testConfigurationsCanBeAnInteger() {
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionConfigurationImpl(CONFIGURATION)
      PsiElement([)('[')
      WFFExpressionConfigurationIdImpl(CONFIGURATION_ID)
        PsiElement(ID)('CONFIGURATION')
        PsiElement(.)('.')
        WFFExpressionUserStringImpl(USER_STRING)
          PsiElement(INTEGER)('0')
      PsiElement(])(']')
          """
        .trimIndent(),
      // missing closing bracket
      toParseTreeText("[CONFIGURATION.0]"),
    )
  }

  fun testCanOnlyHaveOneRootExpression() {
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionNumberImpl(NUMBER)
      PsiElement(INTEGER)('1')
  PsiErrorElement:'1' unexpected
    PsiElement(INTEGER)('1')
          """
        .trimIndent(),
      toParseTreeText("1 1"),
    )
  }
}
