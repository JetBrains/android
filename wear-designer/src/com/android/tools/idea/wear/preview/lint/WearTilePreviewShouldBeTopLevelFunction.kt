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

import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.hasTilePreviewAnnotation
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class WearTilePreviewShouldBeTopLevelFunction : WearTilePreviewInspectionBase() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastVisitorAdapter(
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
          val sourcePsi = node.sourcePsi
          // kotlin can declare named functions nested inside other functions
          if (sourcePsi !is KtNamedFunction) {
            return super.visitLambdaExpression(node)
          }

          val hasTilePreviewAnnotation =
            sourcePsi.annotationEntries
              .mapNotNull { it.toUElement(UAnnotation::class.java) }
              .any { it.hasTilePreviewAnnotation() }

          if (!hasTilePreviewAnnotation) {
            return super.visitLambdaExpression(node)
          }

          holder.registerProblem(
            node.sourcePsi ?: return super.visitLambdaExpression(node),
            message("inspection.top.level.function"),
            ProblemHighlightType.ERROR,
          )

          return super.visitLambdaExpression(node)
        }

        override fun visitMethod(node: UMethod): Boolean {
          if (!node.hasTilePreviewAnnotation()) {
            return super.visitMethod(node)
          }
          if (node.isValidPreviewLocation()) {
            return super.visitMethod(node)
          }
          holder.registerProblem(
            node.sourcePsi ?: return super.visitMethod(node),
            message("inspection.top.level.function"),
            ProblemHighlightType.ERROR,
          )
          return super.visitMethod(node)
        }
      },
      true,
    )
  }

  override fun getStaticDescription() = message("inspection.top.level.function")

  private fun UElement.isValidPreviewLocation(): Boolean {
    val isDeclaredInAnotherFunction = getParentOfType<UMethod>() != null
    if (isDeclaredInAnotherFunction) return false

    // if the function doesn't have a containing class, there won't be problems instantiating it
    val containingClass = getContainingUClass() ?: return true

    val isClassDeclaredInAnotherClass = containingClass.getParentOfType<UClass>() != null
    if (isClassDeclaredInAnotherClass) return false

    val constructors = containingClass.methods.filter { it.isConstructor }
    val classHasDefaultConstructor =
      constructors.isEmpty() || constructors.any { it.uastParameters.isEmpty() }

    return classHasDefaultConstructor
  }
}
