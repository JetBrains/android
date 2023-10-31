/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.compose.aa.code

import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.compose.code.ComposableFunctionRenderParts
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.annotationsByClassId
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.idea.completion.LambdaSignatureTemplates
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * Generates [ComposableFunctionRenderParts] for a given Composable function.
 *
 * Since Composable functions tend to have numerous optional parameters, those are omitted from the rendered parameters and replaced with an
 * ellipsis ("..."). Additional modifications are made to ensure that a lambda can be added in cases where the Composable function requires
 * another Composable as its final argument.
 */
fun KtAnalysisSession.getComposableFunctionRenderParts(functionSymbol: KtFunctionLikeSymbol): ComposableFunctionRenderParts {
  val allParameters = functionSymbol.valueParameters
  val requiredParameters = allParameters.filter { isRequired(it) }
  val lastParamIsComposable = allParameters.lastOrNull()?.let { isComposableFunctionParameter(it) } == true
  val inParens = if (lastParamIsComposable) requiredParameters.dropLast(1) else requiredParameters

  val tail = if (lastParamIsComposable) LambdaSignatureTemplates.DEFAULT_LAMBDA_PRESENTATION else null

  val stringAfterValueParameters = when {
    requiredParameters.size < allParameters.size -> if (inParens.isNotEmpty()) ", ...)" else "...)"
    inParens.isEmpty() && lastParamIsComposable -> null // Don't render an empty pair of parentheses if we're rendering a lambda afterwards.
    else -> ")"
  } ?: return ComposableFunctionRenderParts(null, tail)

  val parameters = renderValueParameters(inParens, stringAfterValueParameters)
  return ComposableFunctionRenderParts(parameters, tail)
}

fun KtAnalysisSession.renderValueParameters(valueParamsInParen: List<KtValueParameterSymbol>, closingString: String) = buildString {
  append("(")
  valueParamsInParen.joinTo(buffer = this) { it.render(KtDeclarationRendererForSource.WITH_SHORT_NAMES) }
  append(closingString)
}

private fun KtAnalysisSession.isRequired(valueParamSymbol: KtValueParameterSymbol): Boolean {
  if (valueParamSymbol.hasDefaultValue) return false

  // TODO(274145999): When we check it with a real AS instance, determine if we can drop this hacky solution or not.
  // The KtValueParameterSymbol we get when running this from [ComposableItemPresentationProvider] for some reason says that optional
  // Composable parameters don't declare a default value, which is incorrect. At the moment, the only way I've found to determine that
  // they're truly optional is by looking at their text.
  return valueParamSymbol.psi?.text?.endsWith("/* = compiled code */") != true
}

fun KtAnalysisSession.isComposableFunctionParameter(valueParamSymbol: KtValueParameterSymbol): Boolean {
  // Since vararg is not a function type parameter, we have to return false for a parameter with a vararg.
  // In FE1.0, it was simple because vararg has an array type and checking that the parameter is a function type returns false.
  // On the other hand, K2's value parameter symbol deliberately unwraps it and returns the element type as a symbol's returnType.
  // We need a separate check for a vararg.
  if (valueParamSymbol.isVararg) return false

  val parameterType = valueParamSymbol.returnType
  // Mimic FE1.0 `KotlinType.isBuiltinFunctionalType`.
  val isBuiltinFunctionalType = parameterType.isFunctionType || parameterType.isSuspendFunctionType
  return isBuiltinFunctionalType &&
         parameterType.annotationsByClassId(ClassId.topLevel(FqName(COMPOSABLE_ANNOTATION_FQ_NAME))).isNotEmpty()
}