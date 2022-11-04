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
import org.jetbrains.kotlin.idea.completion.BasicLookupElementFactory
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.renderer.DescriptorRenderer

fun FunctionDescriptor.getComposableDescriptorRenderer(): DescriptorRenderer? {
  val allParameters = valueParameters
  val requiredParameters = allParameters.filter { !it.declaresDefaultValue() }
  val inParens = if (requiredParameters.hasComposableChildren) requiredParameters.dropLast(1) else requiredParameters
  return when {
    requiredParameters.size < allParameters.size -> SHORT_NAMES_WITH_DOTS
    inParens.isEmpty() && requiredParameters.hasComposableChildren -> {
      // Don't render an empty pair of parenthesis if we're rendering a lambda afterwards.
      null
    }
    else -> BasicLookupElementFactory.SHORT_NAMES_RENDERER
  }
}

private val List<ValueParameterDescriptor>.hasComposableChildren: Boolean
  get() {
    val lastArgType = lastOrNull()?.type ?: return false
    return lastArgType.isBuiltinFunctionalType
           && COMPOSABLE_FQ_NAMES.any { lastArgType.annotations.hasAnnotation(FqName(it)) }
  }

/**
 * A version of [BasicLookupElementFactory.SHORT_NAMES_RENDERER] that adds `, ...)` at the end of the parameters list.
 */
private val SHORT_NAMES_WITH_DOTS = BasicLookupElementFactory.SHORT_NAMES_RENDERER.withOptions {
  val delegate = DescriptorRenderer.ValueParametersHandler.DEFAULT
  valueParametersHandler = object : DescriptorRenderer.ValueParametersHandler {
    override fun appendAfterValueParameter(
      parameter: ValueParameterDescriptor,
      parameterIndex: Int,
      parameterCount: Int,
      builder: StringBuilder
    ) {
      delegate.appendAfterValueParameter(parameter, parameterIndex, parameterCount, builder)
    }

    override fun appendBeforeValueParameter(
      parameter: ValueParameterDescriptor,
      parameterIndex: Int,
      parameterCount: Int,
      builder: StringBuilder
    ) {
      delegate.appendBeforeValueParameter(parameter, parameterIndex, parameterCount, builder)
    }

    override fun appendBeforeValueParameters(parameterCount: Int, builder: StringBuilder) {
      delegate.appendBeforeValueParameters(parameterCount, builder)
    }

    override fun appendAfterValueParameters(parameterCount: Int, builder: StringBuilder) {
      builder.append(if (parameterCount == 0) "...)" else ", ...)")
    }
  }
}
