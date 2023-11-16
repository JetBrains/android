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

import com.android.tools.idea.lang.androidSql.AndroidSqlContext
import com.android.tools.idea.lang.androidSql.AndroidSqlKnownContextInspection
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlParserDefinition
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlBindParameter
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlBooleanLiteral
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlFile
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlLiteralExpression
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPragmaValue
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlVisitor
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

/**
 * Base class for SQL inspections that operate only on PSI files with a known [RoomSqlContext].
 *
 * Similar to [AndroidSqlKnownContextInspection], but requires [RoomSqlContext] instead of the more generic [AndroidSqlContext].
 */
abstract class RoomSqlKnownContextInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return if (isRoomQuery(session)) super.buildVisitor(holder, isOnTheFly, session) else PsiElementVisitor.EMPTY_VISITOR
  }

  private fun isRoomQuery(session: LocalInspectionToolSession): Boolean {
    val query = session.file as? AndroidSqlFile ?: return false
    return RoomSqlContext.Provider().getContext(query) != null
  }

  abstract override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor

}

/** Reports unnamed bind parameters, which are not supported by Room. */
class RoomBindParameterSyntaxInspection : RoomSqlKnownContextInspection() {

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

/** Reports usages of boolean "TRUE" or "FALSE" literals, which are unsupported before API level 30. */
class RoomSqlBooleanLiteralInspection : RoomSqlKnownContextInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : AndroidSqlVisitor() {
      override fun visitBooleanLiteral(literal: AndroidSqlBooleanLiteral) {
        // "true"/"false" are allowed in pragma values regardless of API level.
        if (literal.parent is AndroidSqlPragmaValue) return

        val minSdk = literal.androidFacet?.let { AndroidModel.get(it)?.minSdkVersion?.apiLevel } ?: return
        if (minSdk < 30) {
          val literalValue = when (literal.firstChild?.node?.elementType) {
            AndroidSqlPsiTypes.TRUE -> true
            AndroidSqlPsiTypes.FALSE -> false
            else -> {
              thisLogger().error("Unexpected element type: ${literal.firstChild?.node?.elementType}")
              null
            }
          }

          val problemDescription = "Boolean literals require API level 30 (current min is $minSdk)."

          if (literalValue != null) {
            holder.registerProblem(literal, problemDescription, ProblemHighlightType.WARNING, RoomSqlBooleanLiteralFix(literalValue))
          } else {
            holder.registerProblem(literal, problemDescription, ProblemHighlightType.WARNING)
          }
        }
      }
    }
  }

  private class RoomSqlBooleanLiteralFix(private val literalValue: Boolean) : LocalQuickFix {
    override fun getName(): String =
      if (literalValue) "Replace Boolean literal 'TRUE' with '1'"
      else "Replace Boolean literal 'FALSE' with '0'"

    override fun getFamilyName(): String = "Replace Boolean literal"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val replacementText = if (literalValue) "1" else "0"
      val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "SELECT $replacementText")
      val numericLiteral = requireNotNull(psiFile.findDescendantOfType<AndroidSqlLiteralExpression>())

      descriptor.psiElement.replace(numericLiteral)
    }
  }
}
