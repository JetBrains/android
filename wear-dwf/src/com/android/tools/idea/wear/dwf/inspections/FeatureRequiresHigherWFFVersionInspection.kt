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
package com.android.tools.idea.wear.dwf.inspections

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.wear.dwf.WearDwfBundle.message
import com.android.tools.idea.wear.dwf.dom.raw.CurrentWFFVersionService
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionDataSource
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionFunctionId
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionPsiFile
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionVisitor
import com.android.tools.idea.wear.dwf.dom.raw.expressions.findFunction
import com.android.tools.idea.wear.dwf.dom.raw.findDataSourceDefinition
import com.android.tools.idea.wear.dwf.dom.raw.isReference
import com.android.tools.wear.wff.WFFVersion
import com.android.tools.wear.wff.WFFVersion.WFFVersion4
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

/** Inspection that informs the user if a higher WFF version is required to use a data source. */
class FeatureRequiresHigherWFFVersionInspection : LocalInspectionTool() {

  override fun getStaticDescription() =
    message("inspection.feature.requires.higher.wff.version.description")

  override fun isAvailableForFile(file: PsiFile): Boolean {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get()) return false
    return file is WFFExpressionPsiFile
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val module = holder.file.getModuleSystem()?.module ?: return PsiElementVisitor.EMPTY_VISITOR
    val currentWFFVersion =
      CurrentWFFVersionService.getInstance().getCurrentWFFVersion(module)
        ?: return PsiElementVisitor.EMPTY_VISITOR
    return object : WFFExpressionVisitor() {
      override fun visitFunctionId(functionId: WFFExpressionFunctionId) {
        val requiredWFFVersion = findFunction(functionId.text)?.requiredVersion ?: return
        reportHigherVersionRequiredIfNeeded(
          functionId,
          requiredWFFVersion,
          message("wff.feature.requires.higher.wff.version.function", requiredWFFVersion.version),
        )
      }

      override fun visitDataSource(dataSource: WFFExpressionDataSource) {
        val isReference = dataSource.isReference()
        val requiredVersion =
          if (isReference) WFFVersion4
          else dataSource.findDataSourceDefinition()?.requiredVersion ?: return
        val errorMessageKey =
          if (isReference) "wff.feature.requires.higher.wff.version.reference"
          else "wff.feature.requires.higher.wff.version.datasource"
        reportHigherVersionRequiredIfNeeded(
          dataSource.id,
          requiredWFFVersion = requiredVersion,
          errorMessage = message(errorMessageKey, requiredVersion.version),
        )
      }

      private fun reportHigherVersionRequiredIfNeeded(
        element: PsiElement,
        requiredWFFVersion: WFFVersion,
        errorMessage: String,
      ) {
        if (currentWFFVersion.wffVersion < requiredWFFVersion) {
          holder.registerProblem(element, errorMessage, ProblemHighlightType.ERROR)
        }
      }
    }
  }
}
