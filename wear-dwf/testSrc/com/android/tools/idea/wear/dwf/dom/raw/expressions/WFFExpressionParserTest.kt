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
              PsiElement(ID)('showBackgroundInAfternoon')
            PsiElement(])(']')
        WFFExpressionConditionalOpImpl(CONDITIONAL_OP)
          PsiElement(OPERATORS)('==')
        WFFExpressionLiteralExprImpl(LITERAL_EXPR)
          PsiElement(STRING)('"TRUE"')
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
}
