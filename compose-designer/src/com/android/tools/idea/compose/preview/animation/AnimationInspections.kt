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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getOrCreateValueArgumentList
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.SimpleType

private const val LABEL_PARAMETER = "label"
private const val COMPOSE_ANIMATION_PACKAGE_PREFIX = "androidx.compose.animation"
private const val UPDATE_TRANSITION_FQN = "$COMPOSE_ANIMATION_PACKAGE_PREFIX.core.updateTransition"
private const val TRANSITION_FQN = "$COMPOSE_ANIMATION_PACKAGE_PREFIX.core.Transition"
private const val ANIMATE_PREFIX = "animate" // e.g. animateColor, animateFloat, animateValue

/**
 * Inspection to verify that the `label` parameter is set for `updateTransition` calls that create
 * Compose transition animations. This parameter is used by the animation tooling to identify the
 * transition when inspecting animations in the Animation Preview.
 */
class UpdateTransitionLabelInspection : AbstractKotlinInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor =
    if (session.file.androidFacet != null) {
      object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          val resolvedExpression = expression.resolveToCall() ?: return
          // First, check we're analyzing an updateTransition call
          if (resolvedExpression.resultingDescriptor.fqNameOrNull()?.asString() !=
              UPDATE_TRANSITION_FQN
          )
            return

          // Finally, verify the updateTransition has the `label` parameter set, otherwise show a
          // weak warning.
          if (expression.valueArguments.any {
              resolvedExpression.getParameterForArgument(it)?.name?.asString() == LABEL_PARAMETER
            }
          ) {
            // This updateTransition call already has the label parameter set.
            return
          }
          holder.registerProblem(
            expression,
            message("inspection.update.transition.no.label.parameter.set.description"),
            ProblemHighlightType.WEAK_WARNING,
            AddLabelFieldQuickFix(expression)
          )
        }
      }
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }
}

/**
 * Inspection to verify that the `label` parameter is set for `animate*` (e.g. animateFloat,
 * animateColor) calls that create Compose transition properties. This parameter is used by the
 * animation tooling to identify the transition property when inspecting animations in the Animation
 * Preview. Otherwise, a default name will be used (e.g. FloatProperty, ColorProperty).
 */
class TransitionPropertiesLabelInspection : AbstractKotlinInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor =
    if (session.file.androidFacet != null) {
      object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          val resolvedExpression = expression.resolveToCall() ?: return
          // First, check we're visiting an extension method of Transition<T>
          if ((resolvedExpression.extensionReceiver?.type as? SimpleType)?.fqName?.asString() !=
              TRANSITION_FQN
          )
            return

          // Now, check that we're visiting a method named animate* (e.g. animateFloat,
          // animateValue, animateColor) defined on a compose
          // animation (sub-)package (e.g. androidx.compose.animation,
          // androidx.compose.animation.core).
          val animateCall = resolvedExpression.resultingDescriptor.fqNameOrNull() ?: return
          val shortName = animateCall.shortNameOrSpecial()
          if (shortName.isSpecial ||
              !shortName.asString().startsWith(ANIMATE_PREFIX) ||
              !animateCall.parent().asString().startsWith(COMPOSE_ANIMATION_PACKAGE_PREFIX)
          ) {
            return
          }

          // Finally, verify the animate call has the `label` parameter set, otherwise show a weak
          // warning.
          if (expression.valueArguments.any {
              resolvedExpression.getParameterForArgument(it)?.name?.asString() == LABEL_PARAMETER
            }
          ) {
            // This Transition<T>.animate* call already has the label parameter set.
            return
          }
          holder.registerProblem(
            expression,
            message("inspection.transition.properties.no.label.parameter.set.description"),
            ProblemHighlightType.WEAK_WARNING,
            AddLabelFieldQuickFix(expression)
          )
        }
      }
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }
}

/**
 * Add the `label` parameter to a call expression. For example: `updateTransition(targetState =
 * state)` -> `updateTransition(targetState = state, label = "")` `animateFloat(transitionSpec =
 * ...)` -> `animateFloat(transitionSpec = ..., label = "")`
 */
private class AddLabelFieldQuickFix(updateTransition: KtCallExpression) :
  LocalQuickFixOnPsiElement(updateTransition) {
  override fun getFamilyName() = message("inspection.group.name")

  override fun getText() = message("inspection.no.label.parameter.set.quick.fix.text")

  override fun invoke(
    project: Project,
    file: PsiFile,
    startElement: PsiElement,
    endElement: PsiElement
  ) {
    val psiFactory = KtPsiFactory(project)
    val statementText = "$LABEL_PARAMETER = \"\""
    (startElement as KtCallExpression)
      .getOrCreateValueArgumentList()
      .addArgument(psiFactory.createArgument(statementText))
  }
}
