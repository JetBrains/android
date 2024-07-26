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
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.name.FqName
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
private const val ANIMATED_CONTENT_FQN = "$COMPOSE_ANIMATION_PACKAGE_PREFIX.AnimatedContent"
private const val ANIMATE_AS_STATE_FQN_PREFIX = "$COMPOSE_ANIMATION_PACKAGE_PREFIX.core.animate"
private const val ANIMATE_COLOR_AS_STATE_FQN =
  "$COMPOSE_ANIMATION_PACKAGE_PREFIX.animateColorAsState"
private const val ANIMATE_AS_STATE_FQN_SUFFIX = "AsState"
private const val REMEMBER_INFINITE_TRANSITION_FQN =
  "$COMPOSE_ANIMATION_PACKAGE_PREFIX.core.rememberInfiniteTransition"
private const val CROSSFADE_FQN = "$COMPOSE_ANIMATION_PACKAGE_PREFIX.Crossfade"
private const val TRANSITION_FQN = "$COMPOSE_ANIMATION_PACKAGE_PREFIX.core.Transition"
private const val INFINITE_TRANSITION_FQN =
  "$COMPOSE_ANIMATION_PACKAGE_PREFIX.core.InfiniteTransition"
private const val ANIMATE_PREFIX = "animate" // e.g. animateColor, animateFloat, animateValue
private val ANIMATE_INFINITE = listOf("animateFloat", "animateColor", "animateValue")

/**
 * Inspection to verify that the `label` parameter is set for `updateTransition` calls that create
 * Compose transition animations. This parameter is used by the animation tooling to identify the
 * transition when inspecting animations in the Animation Preview.
 */
class UpdateTransitionLabelInspection : FunctionLabelInspection() {

  override val fqNameCheck: (String) -> Boolean = { it == UPDATE_TRANSITION_FQN }

  override val animationType: String
    get() = message("inspection.animation.type.transition")
}

/**
 * Inspection to verify that the `label` parameter is set for `AnimatedContent` calls. This
 * parameter is used by the animation tooling to identify the transition when inspecting animations
 * in the Animation Preview.
 */
class AnimatedContentLabelInspection : FunctionLabelInspection() {

  override val fqNameCheck: (String) -> Boolean = { it == ANIMATED_CONTENT_FQN }

  override val animationType: String
    get() = message("inspection.animation.type.animated.content")
}

/**
 * Inspection to verify that the `label` parameter is set for `animate*AsState` calls. This
 * parameter is used by the animation tooling to identify the animation when inspecting animations
 * in the Animation Preview.
 */
class AnimateAsStateLabelInspection : FunctionLabelInspection() {

  override val fqNameCheck: (String) -> Boolean = {
    it.startsWith(ANIMATE_AS_STATE_FQN_PREFIX) && it.endsWith(ANIMATE_AS_STATE_FQN_SUFFIX) ||
      it == ANIMATE_COLOR_AS_STATE_FQN
  }

  override val animationType: String
    get() = message("inspection.animation.type.animate.as.state")
}

/**
 * Inspection to verify that the `label` parameter is set for `rememberInfiniteTransition` calls.
 * This parameter is used by the animation tooling to identify the transition when inspecting
 * animations in the Animation Preview.
 */
class InfiniteTransitionLabelInspection : FunctionLabelInspection() {

  override val fqNameCheck: (String) -> Boolean = { it == REMEMBER_INFINITE_TRANSITION_FQN }

  override val animationType: String
    get() = message("inspection.animation.type.remember.infinite.transition")
}

/**
 * Inspection to verify that the `label` parameter is set for `Crossfade` calls. This parameter is
 * used by the animation tooling to identify the transition when inspecting animations in the
 * Animation Preview.
 */
class CrossfadeLabelInspection : FunctionLabelInspection() {

  override val fqNameCheck: (String) -> Boolean = { it == CROSSFADE_FQN }

  override val animationType: String
    get() = message("inspection.animation.type.crossfade")
}

/**
 * Inspection to verify that the `label` parameter is set for these calls. This parameter is used by
 * the animation tooling to identify the transition when inspecting animations in the Animation
 * Preview.
 */
abstract class FunctionLabelInspection : AbstractKotlinInspection() {

  abstract val fqNameCheck: (String) -> Boolean
  abstract val animationType: String

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor =
    if (session.file.androidFacet != null) {
      object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          if (KotlinPluginModeProvider.isK2Mode()) {
            analyze(expression) {
              val resolvedCall = expression.resolveCall()?.successfulFunctionCallOrNull() ?: return
              val callableSymbol = resolvedCall.partiallyAppliedSymbol.symbol

              // For compatibility between versions and with existence of different methods
              // overrides, we need to check if resolved symbol actually can have "label"
              // parameter.
              if (!callableSymbol.valueParameters.any { it.name.toString() == LABEL_PARAMETER })
                return

              // First, check we're analyzing the right call
              val fqName =
                callableSymbol.callableIdIfNonLocal?.asSingleFqName()?.asString() ?: return
              if (!fqNameCheck(fqName)) return

              // Finally, verify the functions has the `label` parameter set, otherwise show a
              // weak warning.
              if (
                expression.valueArguments.any { argument ->
                  resolvedCall.argumentMapping[argument.getArgumentExpression()]
                    ?.symbol
                    ?.name
                    ?.asString() == LABEL_PARAMETER
                }
              ) {
                // This function call already has the label parameter set.
                return
              }
            }
          } else {
            val resolvedExpression = expression.resolveToCall() ?: return
            // For compatibility between versions and with existence of different methods overrides,
            // we need to check if resolved expression actually can have "label" argument.
            if (
              !resolvedExpression.valueArguments.keys.any { it.name.toString() == LABEL_PARAMETER }
            )
              return
            // First, check we're analyzing the right call
            val fqName = resolvedExpression.resultingDescriptor.fqNameOrNull()?.asString() ?: return
            if (!fqNameCheck(fqName)) return

            // Finally, verify the functions has the `label` parameter set, otherwise show a
            // weak warning.
            if (
              expression.valueArguments.any {
                resolvedExpression.getParameterForArgument(it)?.name?.asString() == LABEL_PARAMETER
              }
            ) {
              // This function call already has the label parameter set.
              return
            }
          }
          holder.registerProblem(
            expression.children.firstOrNull() ?: expression,
            message("inspection.animation.no.label.parameter.set.description", animationType),
            ProblemHighlightType.WEAK_WARNING,
            AddLabelFieldQuickFix(expression),
          )
        }
      }
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }

  override fun getStaticDescription() =
    message("inspection.animation.no.label.parameter.set.description", animationType)
}

class InfinitePropertiesLabelInspection : ExtensionLabelInspection() {

  override val fqNameCheck: (String) -> Boolean = { it == INFINITE_TRANSITION_FQN }

  override val animationType: String
    get() = message("inspection.animation.type.infinite.transition.property")

  override val shortFqNameCheck: (FqName) -> Boolean
    get() = { it ->
      // Now, check that we're visiting a method named animate* (e.g. animateFloat,
      // animateValue, animateColor) defined on a compose
      // animation (sub-)package (e.g. androidx.compose.animation,
      // androidx.compose.animation.core).
      val shortName = it.shortNameOrSpecial()
      !shortName.isSpecial &&
        ANIMATE_INFINITE.contains(shortName.toString()) &&
        it.parent().asString().startsWith(COMPOSE_ANIMATION_PACKAGE_PREFIX)
    }
}

class TransitionPropertiesLabelInspection : ExtensionLabelInspection() {

  override val fqNameCheck: (String) -> Boolean = { it == TRANSITION_FQN }

  override val animationType: String
    get() = message("inspection.animation.type.transition.property")

  override val shortFqNameCheck: (FqName) -> Boolean
    get() = { it ->
      // Now, check that we're visiting a method named animate* (e.g. animateFloat,
      // animateValue, animateColor) defined on a compose
      // animation (sub-)package (e.g. androidx.compose.animation,
      // androidx.compose.animation.core).
      val shortName = it.shortNameOrSpecial()
      !shortName.isSpecial &&
        shortName.asString().startsWith(ANIMATE_PREFIX) &&
        it.parent().asString().startsWith(COMPOSE_ANIMATION_PACKAGE_PREFIX)
    }
}

/**
 * Inspection to verify that the `label` parameter is set for `animate*` (e.g. animateFloat,
 * animateColor) calls that create Compose transition properties. This parameter is used by the
 * animation tooling to identify the transition property when inspecting animations in the Animation
 * Preview. Otherwise, a default name will be used (e.g. FloatProperty, ColorProperty).
 */
abstract class ExtensionLabelInspection : AbstractKotlinInspection() {

  abstract val fqNameCheck: (String) -> Boolean
  abstract val shortFqNameCheck: (FqName) -> Boolean
  abstract val animationType: String

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor =
    if (session.file.androidFacet != null) {
      object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          if (KotlinPluginModeProvider.isK2Mode()) {
            analyze(expression) {
              val resolvedCall = expression.resolveCall()?.successfulFunctionCallOrNull() ?: return
              val callableSymbol = resolvedCall.partiallyAppliedSymbol.symbol

              // For compatibility between versions and with existence of different methods
              // overrides, we need to check if resolved symbol actually can have "label"
              // parameter.
              if (!callableSymbol.valueParameters.any { it.name.toString() == LABEL_PARAMETER })
                return

              // First, check we're visiting an extension method of Transition<T>
              val receiverType = callableSymbol.receiverType as? KtNonErrorClassType ?: return
              val fqExtensionName = receiverType.classId.asFqNameString()
              if (!fqNameCheck(fqExtensionName)) return

              // Now, check that we're visiting a method named animate* (e.g. animateFloat,
              // animateValue, animateColor) defined on a compose
              // animation (sub-)package (e.g. androidx.compose.animation,
              // androidx.compose.animation.core).
              val animateCall = callableSymbol.callableIdIfNonLocal?.asSingleFqName() ?: return
              if (!shortFqNameCheck(animateCall)) return

              // Finally, verify the animate call has the `label` parameter set, otherwise show a
              // weak warning.
              if (
                expression.valueArguments.any { argument ->
                  resolvedCall.argumentMapping[argument.getArgumentExpression()]
                    ?.symbol
                    ?.name
                    ?.asString() == LABEL_PARAMETER
                }
              ) {
                // This Transition<T>.animate* call already has the label parameter set.
                return
              }
            }
          } else {
            val resolvedExpression = expression.resolveToCall() ?: return
            // For compatibility between versions and with existence of different methods overrides,
            // we need to check if resolved expression actually can have "label" argument.
            if (
              !resolvedExpression.valueArguments.keys.any { it.name.toString() == LABEL_PARAMETER }
            )
              return
            // First, check we're visiting an extension method of Transition<T>
            val fqExtensionName =
              (resolvedExpression.extensionReceiver?.type as? SimpleType)?.fqName?.asString()
                ?: return
            if (!fqNameCheck(fqExtensionName)) return

            // Now, check that we're visiting a method named animate* (e.g. animateFloat,
            // animateValue, animateColor) defined on a compose
            // animation (sub-)package (e.g. androidx.compose.animation,
            // androidx.compose.animation.core).
            val animateCall = resolvedExpression.resultingDescriptor.fqNameOrNull() ?: return
            if (!shortFqNameCheck(animateCall)) return

            // Finally, verify the animate call has the `label` parameter set, otherwise show a weak
            // warning.
            if (
              expression.valueArguments.any {
                resolvedExpression.getParameterForArgument(it)?.name?.asString() == LABEL_PARAMETER
              }
            ) {
              // This Transition<T>.animate* call already has the label parameter set.
              return
            }
          }
          holder.registerProblem(
            expression,
            message("inspection.animation.no.label.parameter.set.description", animationType),
            ProblemHighlightType.WEAK_WARNING,
            AddLabelFieldQuickFix(expression),
          )
        }
      }
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }

  override fun getStaticDescription() =
    message("inspection.animation.no.label.parameter.set.description", animationType)
}

/**
 * Add the `label` parameter to a call expression. For example: `updateTransition(targetState =
 * state)` -> `updateTransition(targetState = state, label = "")` `animateFloat(transitionSpec =
 * ...)` -> `animateFloat(transitionSpec = ..., label = "")`
 */
private class AddLabelFieldQuickFix(animation: KtCallExpression) :
  LocalQuickFixOnPsiElement(animation) {
  override fun getFamilyName() = message("inspection.group.name")

  override fun getText() = message("inspection.no.label.parameter.set.quick.fix.text")

  override fun invoke(
    project: Project,
    file: PsiFile,
    startElement: PsiElement,
    endElement: PsiElement,
  ) {
    val psiFactory = KtPsiFactory(project)
    val statementText = "$LABEL_PARAMETER = \"\""
    (startElement as KtCallExpression)
      .getOrCreateValueArgumentList()
      .addArgument(psiFactory.createArgument(statementText))
  }
}
