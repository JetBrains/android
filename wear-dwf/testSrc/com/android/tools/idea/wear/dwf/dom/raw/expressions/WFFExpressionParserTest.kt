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
          WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
            PsiElement([)('[')
            PsiElement(ID)('CONFIGURATION.showBackgroundInAfternoon')
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
          WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
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
              PsiElement(NUMBER)('10')
            PsiElement(,)(',')
            WFFExpressionLiteralExprImpl(LITERAL_EXPR)
              PsiElement(NUMBER)('2')
            PsiElement(,)(',')
            WFFExpressionLiteralExprImpl(LITERAL_EXPR)
              PsiElement(NUMBER)('3')
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
    PsiElement(NUMBER)('1')
          """
        .trimIndent(),
      toParseTreeText("1"),
    )

    // we expect an integer after the dot
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    PsiElement(NUMBER)('1')
  PsiErrorElement:<conditional op> expected, got '.'
    PsiElement(BAD_CHARACTER)('.')
          """
        .trimIndent(),
      toParseTreeText("1."),
    )

    // this is a valid number
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    PsiElement(NUMBER)('1.2')
          """
        .trimIndent(),
      toParseTreeText("1.2"),
    )

    // this is not a valid number
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    PsiElement(NUMBER)('1.2')
  PsiErrorElement:<conditional op> expected, got '.'
    PsiElement(BAD_CHARACTER)('.')
  PsiElement(NUMBER)('3')
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
    WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
      PsiElement([)('[')
      PsiElement(ID)('CONFIGURATION.themeColor')
      PsiElement(])(']')
          """
        .trimIndent(),
      toParseTreeText("[CONFIGURATION.themeColor]"),
    )

    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
      PsiElement([)('[')
      PsiElement(ID)('CONFIGURATION.themeColor.1')
      PsiElement(])(']')
          """
        .trimIndent(),
      toParseTreeText("[CONFIGURATION.themeColor.1]"),
    )

    // An incomplete configuration should be considered as a source type
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
      PsiElement([)('[')
      PsiElement(ID)('CONFIGURATION.')
      PsiErrorElement:']' expected
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
    WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
      PsiElement([)('[')
      PsiElement(ID)('CONFIGURATION.themeColor.1')
      PsiErrorElement:']' expected
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
    WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
      PsiElement([)('[')
      PsiElement(ID)('CONFIGURATION.40fc6b01_0756_400d_8903_20a8808c8115')
      PsiElement(])(']')
          """
        .trimIndent(),
      // missing closing bracket
      toParseTreeText("[CONFIGURATION.40fc6b01_0756_400d_8903_20a8808c8115]"),
    )
  }

  fun testConfigurationsIdsCanBeAnInteger() {
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
      PsiElement([)('[')
      PsiElement(ID)('CONFIGURATION.0')
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
    PsiElement(NUMBER)('1')
  PsiErrorElement:'1' unexpected
    PsiElement(NUMBER)('1')
          """
        .trimIndent(),
      toParseTreeText("1 1"),
    )
  }

  fun testParseWeather() {
    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
      PsiElement([)('[')
      PsiElement(ID)('WEATHER.IS_AVAILABLE')
      PsiElement(])(']')
          """
        .trimIndent(),
      toParseTreeText("[WEATHER.IS_AVAILABLE]"),
    )

    assertEquals(
      """
FILE
  WFFExpressionLiteralExprImpl(LITERAL_EXPR)
    WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
      PsiElement([)('[')
      PsiElement(ID)('WEATHER.HOURS.0.CONDITION')
      PsiElement(])(']')
          """
        .trimIndent(),
      toParseTreeText("[WEATHER.HOURS.0.CONDITION]"),
    )
  }
  // Regression test for b/436190988
  fun testParseTernary() {
    assertEquals(
      """
FILE
  WFFExpressionElvisExprImpl(ELVIS_EXPR)
    WFFExpressionConditionalExprImpl(CONDITIONAL_EXPR)
      WFFExpressionLiteralExprImpl(LITERAL_EXPR)
        PsiElement(NUMBER)('0')
      WFFExpressionConditionalOpImpl(CONDITIONAL_OP)
        PsiElement(OPERATORS)('==')
      WFFExpressionLiteralExprImpl(LITERAL_EXPR)
        PsiElement(NUMBER)('0')
    PsiElement(?)('?')
    WFFExpressionLiteralExprImpl(LITERAL_EXPR)
      WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
        PsiElement([)('[')
        PsiElement(ID)('HOUR_0_23_Z')
        PsiElement(])(']')
    PsiElement(:)(':')
    WFFExpressionLiteralExprImpl(LITERAL_EXPR)
      WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
        PsiElement([)('[')
        PsiElement(ID)('HOUR_1_12_Z')
        PsiElement(])(']')
          """
        .trimIndent(),
      toParseTreeText("0 == 0 ?[HOUR_0_23_Z] : [HOUR_1_12_Z]"),
    )
}

  // Regression test for b/436190988
  fun testParseComplexTernary() {
    assertEquals(
      """
FILE
  WFFExpressionElvisExprImpl(ELVIS_EXPR)
    WFFExpressionLiteralExprImpl(LITERAL_EXPR)
      WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
        PsiElement([)('[')
        PsiElement(ID)('CONFIGURATION.leadingZero')
        PsiElement(])(']')
    PsiElement(?)('?')
    WFFExpressionParenExprImpl(PAREN_EXPR)
      PsiElement(()('(')
      WFFExpressionElvisExprImpl(ELVIS_EXPR)
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
            PsiElement([)('[')
            PsiElement(ID)('IS_24_HOUR_MODE')
            PsiElement(])(']')
        PsiElement(?)('?')
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
            PsiElement([)('[')
            PsiElement(ID)('HOUR_0_23_Z')
            PsiElement(])(']')
        PsiElement(:)(':')
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
            PsiElement([)('[')
            PsiElement(ID)('HOUR_1_12_Z')
            PsiElement(])(']')
      PsiElement())(')')
    PsiElement(:)(':')
    WFFExpressionParenExprImpl(PAREN_EXPR)
      PsiElement(()('(')
      WFFExpressionElvisExprImpl(ELVIS_EXPR)
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
            PsiElement([)('[')
            PsiElement(ID)('IS_24_HOUR_MODE')
            PsiElement(])(']')
        PsiElement(?)('?')
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
            PsiElement([)('[')
            PsiElement(ID)('HOUR_0_23')
            PsiElement(])(']')
        PsiElement(:)(':')
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          WFFExpressionDataSourceOrConfigurationImpl(DATA_SOURCE_OR_CONFIGURATION)
            PsiElement([)('[')
            PsiElement(ID)('HOUR_1_12')
            PsiElement(])(']')
      PsiElement())(')')
          """
        .trimIndent(),
      toParseTreeText("[CONFIGURATION.leadingZero] ? ([IS_24_HOUR_MODE] ? [HOUR_0_23_Z] : [HOUR_1_12_Z]) : ([IS_24_HOUR_MODE] ? [HOUR_0_23] : [HOUR_1_12])"),
    )
  }
}
