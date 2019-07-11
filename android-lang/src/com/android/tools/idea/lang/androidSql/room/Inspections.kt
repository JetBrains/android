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
package com.android.tools.idea.lang.androidSql.room

import com.android.tools.idea.lang.androidSql.psi.AndroidSqlBindParameter
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlFile
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlVisitor
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

/**
 * Reports unnamed bind parameters, which are not supported by Room.
 */
class RoomBindParameterSyntaxInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return if (isRoomQuery(session)) super.buildVisitor(holder, isOnTheFly, session) else PsiElementVisitor.EMPTY_VISITOR
  }

  private fun isRoomQuery(session: LocalInspectionToolSession): Boolean {
    val query = session.file as? AndroidSqlFile ?: return false
    return RoomSqlContext.Provider().getContext(query) != null
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : AndroidSqlVisitor() {
      override fun visitBindParameter(parameter: AndroidSqlBindParameter) {
        if (!parameter.isColonNamedParameter) {
          holder.registerProblem(parameter, "Room only supports named parameters with a leading colon, e.g. :argName.")
        }
      }
    }
  }
}