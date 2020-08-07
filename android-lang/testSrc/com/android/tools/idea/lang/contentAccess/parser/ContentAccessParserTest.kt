/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.lang.contentAccess.parser

import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class ContentAccessParserTest : JavaCodeInsightFixtureTestCase() {

  private fun toParseTreeText(input: String): String {
    return DebugUtil.psiToString(myFixture.configureByText(ContentAccessFileType.INSTANCE, input), true, false).trim()
  }

  fun test() {
    assertEquals(
      """
      FILE
        AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
          AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
            AndroidSqlColumnNameImpl(COLUMN_NAME)
              PsiElement(IDENTIFIER)('id')
          PsiElement(=)('=')
          AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
            PsiElement(NUMERIC_LITERAL)('1')
      """.trimIndent(),
      toParseTreeText("id = 1")
    )

    assertEquals(
      """
      FILE
        AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
          AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
            AndroidSqlColumnNameImpl(COLUMN_NAME)
              PsiElement(IDENTIFIER)('id')
          PsiElement(=)('=')
          AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
            AndroidSqlBindParameterImpl(BIND_PARAMETER)
              PsiElement(NAMED_PARAMETER)(':param')
      """.trimIndent(),
      toParseTreeText("id = :param")
    )

    assertEquals(
      """
      FILE
        AndroidSqlAndExpressionImpl(AND_EXPRESSION)
          AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
            AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
              AndroidSqlColumnNameImpl(COLUMN_NAME)
                PsiElement(IDENTIFIER)('favorite_website')
            PsiElement(=)('=')
            AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
              PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''developer.android.com'')
          PsiElement(AND)('AND')
          AndroidSqlComparisonExpressionImpl(COMPARISON_EXPRESSION)
            AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
              AndroidSqlColumnNameImpl(COLUMN_NAME)
                PsiElement(IDENTIFIER)('customer_id')
            PsiElement(>)('>')
            AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
              PsiElement(NUMERIC_LITERAL)('6000')
      """.trimIndent(),
      toParseTreeText("favorite_website = 'developer.android.com' AND customer_id > 6000")
    )

    assertEquals(
      """
      FILE
        AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
          AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
            AndroidSqlColumnNameImpl(COLUMN_NAME)
              PsiElement(IDENTIFIER)('albumid')
          PsiElement(=)('=')
          AndroidSqlExistsExpressionImpl(EXISTS_EXPRESSION)
            PsiElement(()('(')
            AndroidSqlSelectStatementImpl(SELECT_STATEMENT)
              AndroidSqlSelectCoreImpl(SELECT_CORE)
                AndroidSqlSelectCoreSelectImpl(SELECT_CORE_SELECT)
                  PsiElement(SELECT)('SELECT')
                  AndroidSqlResultColumnsImpl(RESULT_COLUMNS)
                    AndroidSqlResultColumnImpl(RESULT_COLUMN)
                      AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        AndroidSqlColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('albumid')
                  AndroidSqlFromClauseImpl(FROM_CLAUSE)
                    PsiElement(FROM)('FROM')
                    AndroidSqlTableOrSubqueryImpl(TABLE_OR_SUBQUERY)
                      AndroidSqlFromTableImpl(FROM_TABLE)
                        AndroidSqlDefinedTableNameImpl(DEFINED_TABLE_NAME)
                          PsiElement(IDENTIFIER)('albums')
                  AndroidSqlWhereClauseImpl(WHERE_CLAUSE)
                    PsiElement(WHERE)('WHERE')
                    AndroidSqlEquivalenceExpressionImpl(EQUIVALENCE_EXPRESSION)
                      AndroidSqlColumnRefExpressionImpl(COLUMN_REF_EXPRESSION)
                        AndroidSqlColumnNameImpl(COLUMN_NAME)
                          PsiElement(IDENTIFIER)('title')
                      PsiElement(=)('=')
                      AndroidSqlLiteralExpressionImpl(LITERAL_EXPRESSION)
                        PsiElement(SINGLE_QUOTE_STRING_LITERAL)(''My Album'')
            PsiElement())(')')
      """.trimIndent(),
      toParseTreeText("""
        albumid = (
           SELECT albumid
           FROM albums
           WHERE title = 'My Album'
        )
      """.trimIndent())
    )
  }
}