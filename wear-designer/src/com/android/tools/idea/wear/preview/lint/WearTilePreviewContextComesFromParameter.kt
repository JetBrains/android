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

import com.android.SdkConstants
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.hasTilePreviewAnnotation
import com.android.tools.idea.wear.preview.isMethodWithTilePreviewSignature
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.lazyPub
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Class that checks that [SdkConstants.CLASS_CONTEXT]s that are used in method calls come from the
 * preview method's parameter.
 *
 * NOTE: This class only checks the preview's immediate body and does not analyse the call stack.
 * It's possible a [SdkConstants.CLASS_CONTEXT] that is not the one passed in the preview parameter
 * is used further down the call stack. This would be resource intensive. Instead, we let the
 * preview fail and surface the error in the designer errors panel instead in that case.
 */
class WearTilePreviewContextComesFromParameter : WearTilePreviewInspectionBase() {
  override fun checkMethod(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): Array<ProblemDescriptor>? {
    if (
      !method.sourcePsi.isMethodWithTilePreviewSignature() || !method.hasTilePreviewAnnotation()
    ) {
      return super.checkMethod(method, manager, isOnTheFly)
    }

    val contextParameter =
      method.uastParameters.firstOrNull {
        it.typeReference?.getQualifiedName() == SdkConstants.CLASS_CONTEXT
      }

    val methodVisitor =
      InvalidContextUsageWithinUMethodVisitor(contextParameter, manager, isOnTheFly)
    method.accept(methodVisitor)
    return methodVisitor.issues.toTypedArray()
  }

  override fun getStaticDescription() = message("inspection.context.comes.from.parameter")
}

private class InvalidContextUsageWithinUMethodVisitor(
  private val contextFromMethodParameter: UParameter?,
  private val manager: InspectionManager,
  private val isOnTheFly: Boolean,
) : AbstractUastVisitor() {

  val psiManager: PsiManager by lazyPub { PsiManager.getInstance(manager.project) }

  val issues = mutableSetOf<ProblemDescriptor>()

  override fun visitCallExpression(node: UCallExpression): Boolean {
    node.valueArguments.forEach { argument ->
      if (!argument.getExpressionType().isContextType()) {
        return@forEach
      }

      if (argument.tryResolve().isContextFromMethodParameter()) {
        return@forEach
      }

      argument.sourcePsi?.let { sourcePsi ->
        issues +=
          manager.createProblemDescriptor(
            sourcePsi,
            message("inspection.context.comes.from.parameter"),
            isOnTheFly,
            LocalQuickFix.EMPTY_ARRAY,
            ProblemHighlightType.ERROR,
          )
      }
    }
    return super.visitCallExpression(node)
  }

  override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
    if (!node.receiver.getExpressionType().isContextType()) {
      return super.visitQualifiedReferenceExpression(node)
    }
    val isSelectorFromAndroidOrAndroidx =
      node.selector.tryResolve()?.kotlinFqName?.asString()?.let {
        it.startsWith("android.") || it.startsWith("androidx.")
      } == true

    // If we are accessing methods from a class that comes from android or androidx, it means we are
    // probably using the context class  in the wrong way. If the selector is coming from a
    // user-declared method, it might be valid.
    // In this case it's better to let the view adapter try to render the preview and surface any
    // errors if it was used in the wrong way.
    if (!isSelectorFromAndroidOrAndroidx) {
      return super.visitQualifiedReferenceExpression(node)
    }

    if (node.receiver.tryResolve().isContextFromMethodParameter()) {
      return super.visitQualifiedReferenceExpression(node)
    }

    node.receiver.sourcePsi?.let { sourcePsi ->
      issues +=
        manager.createProblemDescriptor(
          sourcePsi,
          message("inspection.context.comes.from.parameter"),
          isOnTheFly,
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.ERROR,
        )
    }
    return super.visitQualifiedReferenceExpression(node)
  }

  /**
   * This method checks that the given [PsiElement] is the same as the Context provided in the
   * Preview method's parameter.
   */
  private fun PsiElement?.isContextFromMethodParameter(): Boolean {
    val contextSourcePsi = contextFromMethodParameter?.sourcePsi

    // TODO: depending on https://youtrack.jetbrains.com/issue/KT-68158,
    //  source PSI equivalence check can be fully covered by LC-level equivalence check.
    //  (ULC does, while SLC doesn't for now.)
    // LC-level equivalence check
    return psiManager.areElementsEquivalent(contextSourcePsi, this) ||
      // Source PSI equivalence check
      (this as? KtLightElement<*, *>)?.kotlinOrigin?.isEquivalentTo(contextSourcePsi) == true
  }
}

private fun PsiType?.isContextType() = InheritanceUtil.isInheritor(this, SdkConstants.CLASS_CONTEXT)
