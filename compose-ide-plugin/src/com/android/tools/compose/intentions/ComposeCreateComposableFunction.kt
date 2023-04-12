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
import com.android.tools.compose.getComposableAnnotation
import com.android.tools.compose.isComposableFunction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAndGetResult
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.CallableBuilder
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.CallableInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.FunctionInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.ParameterInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getParameterInfos
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getTypeInfoForTypeArguments
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.guessTypes
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.replaceAnnotations


class ComposeUnresolvedFunctionFixContributor : QuickFixContributor {
  override fun registerQuickFixes(quickFixes: QuickFixes) {
    quickFixes.register(Errors.UNRESOLVED_REFERENCE, ComposeUnresolvedFunctionFixFactory())
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
    if (!parentFunction.isComposableFunction()) return null

    val name = (unresolvedCall.calleeExpression as? KtSimpleNameExpression)?.getReferencedName() ?: return null
    // Composable function usually starts with uppercase first letter.
    if (name.isBlank() || !name[0].isUpperCase()) return null

    val ktCreateCallableFromUsageFix = CreateCallableFromUsageFix(unresolvedCall) {
      listOfNotNull(createNewComposeFunctionInfo(name, it, parentFunction))
    }

    // Since CreateCallableFromUsageFix is no longer an 'open' class, we instead use delegation to customize the text.
    return object : IntentionAction by ktCreateCallableFromUsageFix {
      override fun getText(): String = ComposeBundle.message("create.composable.function") + " '$name'"
    }
  }

  private val composableAnnotation = "@$COMPOSABLE_ANNOTATION_NAME"

  // n.b. Do not cache this CallableInfo anywhere, otherwise it is easy to leak Kotlin descriptors.
  // (see https://github.com/JetBrains/intellij-community/commit/608589428c).
  private fun createNewComposeFunctionInfo(name: String,
                                           element: KtCallExpression,
                                           parentComposableFunction: KtNamedFunction): CallableInfo? {
    val analysisResult = element.analyzeAndGetResult()
    val fullCallExpression = element.getQualifiedExpressionForSelectorOrThis()
    val expectedType = fullCallExpression.guessTypes(analysisResult.bindingContext, analysisResult.moduleDescriptor).singleOrNull()
    if (expectedType != null && KotlinBuiltIns.isUnit(expectedType)) {
      val typeParameters = element.getTypeInfoForTypeArguments()
      val returnType = TypeInfo(expectedType, Variance.OUT_VARIANCE)
      val modifierList = KtPsiFactory(element).createModifierList(composableAnnotation)
      val containers = element.getQualifiedExpressionForSelectorOrThis().getExtractionContainers()

      val parameters = if (element.valueArguments.lastOrNull() is KtLambdaArgument) {
        // If the last argument is a lambda, treat it as a `content` parameter containing another Composable.
        // In this case, we want the resulting argument name to be "content" and it should have a @Composable attribute.
        val parameterInfos = element.getParameterInfos()
        val modifiedLastParameter = parameterInfos.last().let {
          ParameterInfo(
            typeInfo = ComposableLambdaTypeInfo(it.typeInfo, parentComposableFunction),
            nameSuggestions = listOf("content") + it.nameSuggestions)
        }

        parameterInfos.dropLast(1) + listOf(modifiedLastParameter)
      }
      else {
        element.getParameterInfos()
      }

      return FunctionInfo(name, TypeInfo.Empty, returnType, containers, parameters, typeParameters, modifierList = modifierList)
    }
    return null
  }

  /** Wrapper around [TypeInfo] adding a @Composable annotation to the argument type. */
  private class ComposableLambdaTypeInfo(private val wrapped: TypeInfo,
                                         private val parentComposableFunction: KtNamedFunction) : TypeInfo(wrapped.variance) {
    override fun getPossibleTypes(builder: CallableBuilder): List<KotlinType> {
      val wrappedTypes = wrapped.getPossibleTypes(builder)
      val composableAnnotationDescriptor = parentComposableFunction.getComposableAnnotation()?.resolveToDescriptorIfAny()

      if (composableAnnotationDescriptor == null) {
        thisLogger().warn("Could not resolve @Composable annotation descriptor.")
        return wrappedTypes
      }

      return wrappedTypes.map {
        val newAnnotations = Annotations.create(it.annotations + listOf(composableAnnotationDescriptor))
        it.replaceAnnotations(newAnnotations)
      }
    }
  }
}
