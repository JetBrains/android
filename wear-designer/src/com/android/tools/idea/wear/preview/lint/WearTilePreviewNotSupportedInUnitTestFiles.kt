/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.lint

import com.android.tools.idea.preview.annotations.findAllAnnotationsInGraph
import com.android.tools.idea.projectsystem.isUnitTestFile
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.isTilePreviewAnnotation
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

/**
 * Inspection that checks that functions annotated with `@Preview`, or with a MultiPreview, are not
 * in a unit test file.
 */
class WearTilePreviewNotSupportedInUnitTestFiles : AbstractKotlinInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor =
    if (session.file.androidFacet != null || ApplicationManager.getApplication().isUnitTestMode) {
      object : KtVisitorVoid() {
        override fun visitElement(element: PsiElement) {
          super.visitElement(element)
          if (element !is PsiMethod && element !is KtFunction) return

          // If the element is not in a unit test file, then this inspection has nothing to do
          if (!isUnitTestFile(element.project, element.containingFile.virtualFile)) return

          // We are only interested in methods annotated with the tile preview annotation
          if (!element.toUElement(UMethod::class.java).hasTilePreviewAnnotation()) return

          holder.registerProblem(
            element,
            message("inspection.unit.test.files"),
            ProblemHighlightType.ERROR,
          )
        }
      }
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }

  override fun getStaticDescription() = message("inspection.unit.test.files")

  override fun getGroupDisplayName() = message("inspection.group.name")
}

private fun UMethod?.hasTilePreviewAnnotation() =
  this?.findAllAnnotationsInGraph { it.isTilePreviewAnnotation() }?.any() ?: false
