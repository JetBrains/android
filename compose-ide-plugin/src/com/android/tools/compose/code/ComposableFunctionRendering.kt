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

import com.android.tools.compose.aa.code.getComposableFunctionRenderParts
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.completion.BasicLookupElementFactory.Companion.SHORT_NAMES_RENDERER
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.renderer.DescriptorRenderer.ValueParametersHandler

/**
 * Represents parts of a Composable function to be used for rendering in various menus or dialogs.
 */
data class ComposableFunctionRenderParts(
  val totalParameterCount: Int,
  val parameters: String?,
  val tail: String?,
)

@OptIn(KaAllowAnalysisOnEdt::class)
fun KtDeclaration.getComposableFunctionRenderParts(): ComposableFunctionRenderParts? {
  return allowAnalysisOnEdt {
    analyze(this) {
      val functionLikeSymbol =
          this@getComposableFunctionRenderParts.symbol as? KaFunctionSymbol ?: return null
      getComposableFunctionRenderParts(functionLikeSymbol)
    }
  }
}

/** A version of [SHORT_NAMES_RENDERER] that adds `, ...)` at the end of the parameters list. */
private val SHORT_NAMES_RENDERER_WITH_DOTS =
  SHORT_NAMES_RENDERER.withOptions {
    valueParametersHandler =
      object : ValueParametersHandler by ValueParametersHandler.DEFAULT {
        override fun appendAfterValueParameters(parameterCount: Int, builder: StringBuilder) {
          if (parameterCount > 0) builder.append(", ")
          builder.append("...)")
        }
      }
  }
