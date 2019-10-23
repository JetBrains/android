/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.lang.roomSql

import com.android.tools.idea.lang.roomSql.psi.RoomBindParameter
import com.android.tools.idea.lang.roomSql.psi.RoomColumnName
import com.android.tools.idea.lang.roomSql.psi.RoomDefinedTableName
import com.android.tools.idea.lang.roomSql.psi.RoomDeleteStatement
import com.android.tools.idea.lang.roomSql.psi.RoomInsertStatement
import com.android.tools.idea.lang.roomSql.psi.RoomSelectStatement
import com.android.tools.idea.lang.roomSql.psi.RoomSelectedTableName
import com.android.tools.idea.lang.roomSql.psi.RoomSqlFile
import com.android.tools.idea.lang.roomSql.psi.RoomUpdateStatement
import com.android.tools.idea.lang.roomSql.psi.RoomVisitor
import com.android.tools.idea.lang.roomSql.psi.RoomWithClauseStatement
import com.android.tools.idea.lang.roomSql.resolution.RoomColumnPsiReference
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale

/**
 * Inspection for the RoomSql language that only does something when running on a PSI file that's injected into a Room query.
 */
abstract class RoomQueryOnlyInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return if (isThisRoomQuery(session)) super.buildVisitor(holder, isOnTheFly, session) else PsiElementVisitor.EMPTY_VISITOR
  }

  private fun isThisRoomQuery(session: LocalInspectionToolSession) = (session.file as? RoomSqlFile)?.findHostRoomAnnotation() != null
}

class RoomUnresolvedReferenceInspection : RoomQueryOnlyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : RoomVisitor() {

      override fun visitColumnName(columnName: RoomColumnName) {
        super.visitColumnName(columnName)
        checkReference(columnName)
      }

      override fun visitDefinedTableName(definedTableName: RoomDefinedTableName) {
        super.visitDefinedTableName(definedTableName)
        checkReference(definedTableName)
      }

      override fun visitSelectedTableName(selectedTableName: RoomSelectedTableName) {
        super.visitSelectedTableName(selectedTableName)
        checkReference(selectedTableName)
      }

      override fun visitBindParameter(bindParameter: RoomBindParameter) {
        super.visitBindParameter(bindParameter)
        checkReference(bindParameter)
      }

      private fun checkReference(referenceElement: PsiElement) {
        val roomSqlFile = referenceElement.containingFile as? RoomSqlFile ?: return

        // FRANKENSTEIN_INJECTION means the file should not be checked, see e.g. FrankensteinErrorFilter. This may be the case if we're
        // parsing a string expression and for some reason cannot compute its value. KotlinLanguageInjector marks every injection in a
        // string template as such, see the splitLiteralToInjectionParts function and b/77211318. See KT-25906.
        if (roomSqlFile.getUserData(InjectedLanguageManager.FRANKENSTEIN_INJECTION) == true) return

        // Make sure we're inside a Room annotation, otherwise we don't know the schema.
        if (roomSqlFile.findHostRoomAnnotation() == null) return

        if (!(isWellUnderstood(PsiTreeUtil.findPrevParent(referenceElement.containingFile, referenceElement)))) return

        val reference = referenceElement.reference ?: return
        if (reference.resolve() == null && !isRowIdReference(reference)) {
          holder.registerProblem(reference)
        }
      }

      /**
       * Checks if the given reference is a column reference to the magic ROWID column.
       *
       * ROWID can be referenced in queries (for most tables) using multiple special names, but doesn't need to be declared. There's also
       * little point suggesting it in code completion, so we don't insert this column into the schema, instead we just ignore references
       * to it, which are most likely correct (edge cases will be caught by the Room query compiler).
       *
       * See [SQLite docs](https://sqlite.org/lang_createtable.html#rowid).
       */
      private fun isRowIdReference(reference: PsiReference): Boolean {
        return if (reference is RoomColumnPsiReference) {
          when (reference.element.nameAsString.toLowerCase(Locale.US)) {
            "rowid", "_rowid_", "oid" -> true
            "docid" -> true // TODO(b/120407095): do this only in FTS tables.
            else -> false
          }
        } else {
          false
        }
      }

      /**
       * Checks if we have understand the given query type enough to highlight unresolved references.
       */
      private fun isWellUnderstood(stmt: PsiElement): Boolean = when (stmt) {
        is RoomSelectStatement, is RoomUpdateStatement, is RoomInsertStatement, is RoomDeleteStatement, is RoomWithClauseStatement -> true
        else -> false
      }
    }
  }
}

class RoomBindParameterSyntaxInspection : RoomQueryOnlyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : RoomVisitor() {
      override fun visitBindParameter(parameter: RoomBindParameter) {
        if (!parameter.isColonNamedParameter) {
          holder.registerProblem(parameter, "Room only supports named parameters with a leading colon, e.g. :argName.")
        }
      }
    }
  }
}
