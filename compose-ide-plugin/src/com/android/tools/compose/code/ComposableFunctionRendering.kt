/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.compose.code

import com.android.tools.compose.COMPOSABLE_FQ_NAMES
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.completion.BasicLookupElementFactory.Companion.SHORT_NAMES_RENDERER
import org.jetbrains.kotlin.idea.completion.LambdaSignatureTemplates
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.renderer.DescriptorRenderer.ValueParametersHandler

/**
 * Represents parts of a Composable function to be used for rendering in various menus or dialogs.
 */
data class ComposableFunctionRenderParts(val parameters: String?, val tail: String?)

/**
 * Generates [ComposableFunctionRenderParts] for a given Composable function.
 *
 * Since Composable functions tend to have numerous optional parameters, those are omitted from the rendered parameters and replaced with an
 * ellipsis ("..."). Additional modifications are made to ensure that a lambda can be added in cases where the Composable function requires
 * another Composable as its final argument.
 */
fun FunctionDescriptor.getComposableFunctionRenderParts(): ComposableFunctionRenderParts {
  val allParameters = valueParameters
  val requiredParameters = allParameters.filter { it.isRequired() }
  val lastParamIsComposable = allParameters.lastOrNull()?.isComposableFunctionParameter() == true
  val inParens = if (lastParamIsComposable) requiredParameters.dropLast(1) else requiredParameters

  val descriptorRenderer = when {
    requiredParameters.size < allParameters.size -> SHORT_NAMES_RENDERER_WITH_DOTS
    inParens.isEmpty() && lastParamIsComposable -> null // Don't render an empty pair of parentheses if we're rendering a lambda afterwards.
    else -> SHORT_NAMES_RENDERER
  }
  val parameters = descriptorRenderer?.renderValueParameters(inParens, false)

  val tail = if (lastParamIsComposable) LambdaSignatureTemplates.DEFAULT_LAMBDA_PRESENTATION else null

  return ComposableFunctionRenderParts(parameters, tail)
}

private fun ValueParameterDescriptor.isRequired(): Boolean {
  if (declaresDefaultValue()) return false

  // The ValueParameterDescriptor we get when running this from [ComposableItemPresentationProvider] for some reason says that optional
  // Composable parameters don't declare a default value, which is incorrect. At the moment, the only way I've found to determine that
  // they're truly optional is by looking at their text.
  return source.getPsi()?.text?.endsWith("/* = compiled code */") != true
}

fun ValueParameterDescriptor.isComposableFunctionParameter(): Boolean {
  val parameterType = type
  return parameterType.isBuiltinFunctionalType && COMPOSABLE_FQ_NAMES.any { parameterType.annotations.hasAnnotation(FqName(it)) }
}

/**
 * A version of [SHORT_NAMES_RENDERER] that adds `, ...)` at the end of the parameters list.
 */
private val SHORT_NAMES_RENDERER_WITH_DOTS = SHORT_NAMES_RENDERER.withOptions {
  valueParametersHandler = object : ValueParametersHandler by ValueParametersHandler.DEFAULT {
    override fun appendAfterValueParameters(parameterCount: Int, builder: StringBuilder) {
      if (parameterCount > 0) builder.append(", ")
      builder.append("...)")
    }
  }
}
