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

import com.android.tools.idea.lang.androidSql.psi.AndroidSqlBindParameter
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlVisitor
import com.intellij.lang.ASTFactory
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Returns a SQLite statement where named parameters have been replaced with positional parameters (?)
 * and the list of named parameters in the original statement.
 * @param psiElement The [PsiElement] corresponding to a SQLite statement.
 * @return The text of the SQLite statement with positional parameters and the list of named parameters.
 */
fun replaceNamedParametersWithPositionalParameters(psiElement: PsiElement): ParsedSqliteStatement {
  val psiElementCopy = psiElement.copy()
  val parametersNames = mutableListOf<String>()

  invokeAndWaitIfNeeded {
    runUndoTransparentWriteAction {
      val visitor = object : AndroidSqlVisitor() {
        override fun visitBindParameter(parameter: AndroidSqlBindParameter) {
          parametersNames.add(parameter.text)
          parameter.node.replaceChild(parameter.node.firstChildNode, ASTFactory.leaf(AndroidSqlPsiTypes.NUMBERED_PARAMETER, "?"))
        }
      }

      PsiTreeUtil.processElements(psiElementCopy) { it.accept(visitor); true }
    }
  }
  return ParsedSqliteStatement(psiElementCopy.text, parametersNames)
}

/**
 * @param statementText SQLite statement where parameters have been replaced with '?'
 * @param parameters the name of the parameters that have been replaced with '?'
 */
data class ParsedSqliteStatement(val statementText: String, val parameters: List<String>)