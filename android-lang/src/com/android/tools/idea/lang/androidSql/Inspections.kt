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
package com.android.tools.idea.lang.androidSql

import com.android.tools.idea.lang.androidSql.psi.AndroidSqlBindParameter
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlColumnName
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlDefinedTableName
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlDeleteStatement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlFile
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlInsertStatement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlSelectStatement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlSelectedTableName
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlUpdateStatement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlVisitor
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlWithClauseStatement
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil

/**
 * Base class for SQL inspections that only do something when running on a PSI file with a known [AndroidSqlContext].
 */
abstract class AndroidSqlKnownContextInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return if (isContextKnown(session)) super.buildVisitor(holder, isOnTheFly, session) else PsiElementVisitor.EMPTY_VISITOR
  }

  private fun isContextKnown(session: LocalInspectionToolSession) = (session.file as? AndroidSqlFile)?.sqlContext != null
}

/**
 * Reports unresolved SQL references.
 */
class AndroidSqlUnresolvedReferenceInspection : AndroidSqlKnownContextInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : AndroidSqlVisitor() {

      override fun visitColumnName(columnName: AndroidSqlColumnName) {
        super.visitColumnName(columnName)
        checkReference(columnName)
      }

      override fun visitDefinedTableName(definedTableName: AndroidSqlDefinedTableName) {
        super.visitDefinedTableName(definedTableName)
        checkReference(definedTableName)
      }

      override fun visitSelectedTableName(selectedTableName: AndroidSqlSelectedTableName) {
        super.visitSelectedTableName(selectedTableName)
        checkReference(selectedTableName)
      }

      override fun visitBindParameter(bindParameter: AndroidSqlBindParameter) {
        super.visitBindParameter(bindParameter)
        checkReference(bindParameter)
      }

      private fun checkReference(referenceElement: PsiElement) {
        val sqlFile = referenceElement.containingFile as? AndroidSqlFile ?: return

        // FRANKENSTEIN_INJECTION means the file should not be checked, see e.g. FrankensteinErrorFilter. This may be the case if we're
        // parsing a string expression and for some reason cannot compute its value. KotlinLanguageInjector marks every injection in a
        // string template as such, see the splitLiteralToInjectionParts function and b/77211318. See KT-25906.
        if (sqlFile.getUserData(InjectedLanguageManager.FRANKENSTEIN_INJECTION) == true) return

        if (!(isWellUnderstood(PsiTreeUtil.findPrevParent(referenceElement.containingFile, referenceElement)))) return

        val reference = referenceElement.reference ?: return
        if (reference.resolve() == null) {
          holder.registerProblem(reference)
        }
      }

      /**
       * Checks if we have understand the given query type enough to highlight unresolved references.
       */
      private fun isWellUnderstood(stmt: PsiElement): Boolean = when (stmt) {
        is AndroidSqlSelectStatement,
        is AndroidSqlUpdateStatement,
        is AndroidSqlInsertStatement,
        is AndroidSqlDeleteStatement,
        is AndroidSqlWithClauseStatement -> true
        else -> false
      }
    }
  }
}
