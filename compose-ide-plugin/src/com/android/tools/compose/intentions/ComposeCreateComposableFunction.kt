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
package com.android.tools.compose.intentions

import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.isComposableFunction
import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAndGetResult
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.CallableInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.FunctionInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getParameterInfos
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getTypeInfoForTypeArguments
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.guessTypes
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


class ComposeUnresolvedFunctionFixContributor : QuickFixContributor {
  override fun registerQuickFixes(quickFixes: QuickFixes) {
    if (StudioFlags.COMPOSE_EDITOR_SUPPORT.get()) {
      quickFixes.register(Errors.UNRESOLVED_REFERENCE, ComposeUnresolvedFunctionFixFactory())
    }
  }
}

/**
 * Creates quick fix(IntentionAction) for an unresolved reference inside a Composable function.
 *
 * Created action creates new function with @Composable annotation.
 *
 * Example:
 * For
 *
 * @Composable
 * fun myComposable() {
 *  <caret>newFunction()
 * }
 *
 * creates
 *
 * @Composable
 * fun newFunction() {
 *  TODO("Not yet implemented")
 * }
 *
 */
private class ComposeUnresolvedFunctionFixFactory : KotlinSingleIntentionActionFactory() {
  override fun createAction(diagnostic: Diagnostic): IntentionAction? {
    val unresolvedCall = diagnostic.psiElement.parent as? KtCallExpression ?: return null
    val parentFunction = unresolvedCall.getStrictParentOfType<KtNamedFunction>() ?: return null
    if (parentFunction.isComposableFunction()) {
      val functionInfo = createNewComposeFunctionInfo(unresolvedCall)
      if (functionInfo != null) {
        return CreateCallableFromUsageFix(unresolvedCall) {
          listOf(functionInfo)
        }
        // fixme-ank6:
        //return object : CreateCallableFromUsageFix<KtCallExpression>(unresolvedCall, listOf(functionInfo)) {
        //  override fun getText() = ComposeBundle.message("create.composable.function") + " " + "'${callableInfos.first().name}'"
        //}
      }
    }
    return null
  }

  private val composableAnnotation = "@$COMPOSABLE_ANNOTATION_NAME"

  private fun createNewComposeFunctionInfo(element: KtCallExpression): CallableInfo? {
    val name = element.calleeExpression.safeAs<KtSimpleNameExpression>()?.getReferencedName()?.takeIf { it.isNotBlank() } ?: return null
    // Composable function usually starts with uppercase first letter.
    if (!name[0].isUpperCase()) return null
    val analysisResult = element.analyzeAndGetResult()
    val fullCallExpression = element.getQualifiedExpressionForSelectorOrThis()
    val expectedType = fullCallExpression.guessTypes(analysisResult.bindingContext, analysisResult.moduleDescriptor).singleOrNull()
    if (expectedType != null && KotlinBuiltIns.isUnit(expectedType)) {
      val parameters = element.getParameterInfos()
      val typeParameters = element.getTypeInfoForTypeArguments()
      val returnType = TypeInfo(expectedType, Variance.OUT_VARIANCE)
      val modifierList = KtPsiFactory(element).createModifierList(composableAnnotation)
      val containers = element.getQualifiedExpressionForSelectorOrThis().getExtractionContainers()
      return FunctionInfo(name, TypeInfo.Empty, returnType, containers, parameters, typeParameters, modifierList = modifierList)
    }
    return null
  }
}
