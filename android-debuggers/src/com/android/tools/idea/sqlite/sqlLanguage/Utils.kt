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
package com.android.tools.idea.sqlite.sqlLanguage

import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlParserDefinition
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlBindParameter
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlColumnRefExpression
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlComparisonExpression
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlEquivalenceExpression
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlInExpression
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlVisitor
import com.android.tools.idea.sqlite.controllers.SqliteParameter
import com.android.tools.idea.sqlite.controllers.SqliteParameterValue
import com.android.tools.idea.sqlite.model.SqliteValue
import com.intellij.lang.ASTFactory
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import java.util.Deque

/**
 * Returns a SQLite statement where named parameters have been replaced with positional parameters (?)
 * and the list of named parameters in the original statement.
 * @param psiElement The [PsiElement] corresponding to a SQLite statement.
 * @return The text of the SQLite statement with positional parameters and the list of named parameters.
 */
fun replaceNamedParametersWithPositionalParameters(psiElement: PsiElement): ParsedSqliteStatement {
  // Can't do psiElement.copy because cloning the view provider of the RoomSql PsiFile doesn't work.
  val psiElementCopy = AndroidSqlParserDefinition.parseSqlQuery(psiElement.project, psiElement.text)
  val parametersNames = mutableListOf<SqliteParameter>()

  invokeAndWaitIfNeeded {
    runUndoTransparentWriteAction {
      val visitor = object : AndroidSqlVisitor() {
        override fun visitBindParameter(bindParameter: AndroidSqlBindParameter) {
          val bindParameterText = bindParameter.text

          val parameterName = if (!bindParameterText.startsWith("?")) {
            bindParameterText
          } else {
            val parent = bindParameter.parentOfType(AndroidSqlEquivalenceExpression::class, AndroidSqlComparisonExpression::class)

            // If there is no parent of type EquivalenceExpression or ComparisonExpression keep '?' as the variable name.
            // Otherwise use the name of the column.
            parent?.children?.filterIsInstance<AndroidSqlColumnRefExpression>()?.firstOrNull()?.text ?: bindParameterText
          }

          parametersNames.add(SqliteParameter(parameterName, parentIsInExpression(bindParameter)))
          bindParameter.node.replaceChild(bindParameter.node.firstChildNode, ASTFactory.leaf(AndroidSqlPsiTypes.NUMBERED_PARAMETER, "?"))
        }
      }

      PsiTreeUtil.processElements(psiElementCopy) { it.accept(visitor); true }
    }
  }
  return ParsedSqliteStatement(psiElementCopy.text, parametersNames)
}

fun expandCollectionParameters(psiElement: PsiElement, parameterValues: Deque<SqliteParameterValue>): PsiElement {
  // Can't do psiElement.copy because cloning the view provider of the RoomSql PsiFile doesn't work.
  val psiElementCopy = AndroidSqlParserDefinition.parseSqlQuery(psiElement.project, psiElement.text)

  invokeAndWaitIfNeeded {
    runUndoTransparentWriteAction {
      val visitor = object : AndroidSqlVisitor() {
        override fun visitBindParameter(bindParameter: AndroidSqlBindParameter) {
          val parameterValue = parameterValues.pollFirst()
          if (parentIsInExpression(bindParameter)) {
            val collectionValue = parameterValue as SqliteParameterValue.CollectionValue

            if (collectionValue.value.size > 1) {
              val listOfQuestionMarks = collectionValue.value.map { "?" }
              val text = listOfQuestionMarks.joinToString(", ")

              // doing this makes the tree not valid.
              val leaf = ASTFactory.leaf(AndroidSqlPsiTypes.BIND_PARAMETER, text)
              bindParameter.node.replaceChild(bindParameter.node.firstChildNode, leaf)
            }
          }
        }
      }

      PsiTreeUtil.processElements(psiElementCopy) { it.accept(visitor); true }
    }
  }

  // it's necessary to create a new PSI element, because of the wrong replacement done above.
  return AndroidSqlParserDefinition.parseSqlQuery(psiElementCopy.project, psiElementCopy.text)
}

/**
 * Replaces all [AndroidSqlBindParameter]s with parameter values in [parameterValues],
 * matching them by the order they have in the queue.
 */
fun inlineParameterValues(psiElement: PsiElement, parameterValues: Deque<SqliteValue>): String {
  // Can't do psiElement.copy because cloning the view provider of the RoomSql PsiFile doesn't work.
  val psiElementCopy = AndroidSqlParserDefinition.parseSqlQuery(psiElement.project, psiElement.text)

  invokeAndWaitIfNeeded {
    runUndoTransparentWriteAction {
      val visitor = object : AndroidSqlVisitor() {
        override fun visitBindParameter(bindParameter: AndroidSqlBindParameter) {
          val leaf = when (val sqliteValue = parameterValues.pollFirst()) {
            is SqliteValue.StringValue -> {
              ASTFactory.leaf(AndroidSqlPsiTypes.SINGLE_QUOTE_STRING_LITERAL, AndroidSqlLexer.getValidStringValue(sqliteValue.value))
            }
            is SqliteValue.NullValue -> ASTFactory.leaf(AndroidSqlPsiTypes.NULL, "null")
          }

          bindParameter.node.replaceChild(bindParameter.node.firstChildNode, leaf)
        }
      }

      PsiTreeUtil.processElements(psiElementCopy) { it.accept(visitor); true }
    }
  }
  return psiElementCopy.text
}

/**
 * Returns true if the SqliteStatement in [psiElement] has at least one [AndroidSqlBindParameter].
 */
fun needsBinding(psiElement: PsiElement): Boolean {
  var needsBinding = false
  invokeAndWaitIfNeeded {
    runUndoTransparentWriteAction {
      val visitor = object : AndroidSqlVisitor() {
        override fun visitBindParameter(bindParameter: AndroidSqlBindParameter) {
          needsBinding = true
        }
      }

      // stop visiting after needsBinding becomes true
      PsiTreeUtil.processElements(psiElement) { it.accept(visitor); !needsBinding }
    }
  }
  return needsBinding
}

private fun parentIsInExpression(bindParameter: AndroidSqlBindParameter): Boolean {
  return bindParameter.parent.parent is AndroidSqlInExpression
}

/**
 * @param statementText SQLite statement where parameters have been replaced with '?'
 * @param parameters the name of the parameters that have been replaced with '?'
 */
data class ParsedSqliteStatement(val statementText: String, val parameters: List<SqliteParameter>)