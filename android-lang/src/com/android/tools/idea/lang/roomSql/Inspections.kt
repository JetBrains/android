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

import com.android.tools.idea.lang.roomSql.psi.RoomColumnName
import com.android.tools.idea.lang.roomSql.psi.RoomTableName
import com.android.tools.idea.lang.roomSql.psi.RoomVisitor
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

class UnresolvedRoomSqlReferenceInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : RoomVisitor() {
      override fun visitColumnName(columnName: RoomColumnName) {
        super.visitColumnName(columnName)
        checkReference(columnName)
      }

      override fun visitTableName(tableName: RoomTableName) {
        super.visitTableName(tableName)
        checkReference(tableName)
      }

      private fun checkReference(columnName: PsiElement) {
        val reference = columnName.reference as? SqlReference ?: return
        if (reference.warnIfUnresolved && reference.resolve() == null) {
          holder.registerProblem(reference)
        }
      }
    }
  }
}
