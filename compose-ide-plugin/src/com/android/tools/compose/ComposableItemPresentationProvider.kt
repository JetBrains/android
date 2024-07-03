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
package com.android.tools.compose

import com.android.tools.compose.code.getComposableFunctionRenderParts
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationProvider
import org.jetbrains.kotlin.idea.presentation.KotlinFunctionPresentation
import org.jetbrains.kotlin.psi.KtFunction

/**
 * [ItemPresentationProvider] which overrides default behavior for displaying a method in a menu
 * (eg, in "Add Imports").
 *
 * Composable methods will be displayed as "@Composable FunctionName". Other functions will maintain
 * default display text (ie, "functionName(arg1, arg2)".
 */
class ComposableItemPresentationProvider : ItemPresentationProvider<KtFunction> {
  override fun getPresentation(function: KtFunction): ItemPresentation? {
    if (function.name?.isNotEmpty() == true && function.hasComposableAnnotation()) {
      return ComposableFunctionPresentation(function)
    }

    return null
  }

  /**
   * Presentation for composable functions. Based on the default [KotlinFunctionPresentation], with
   * modifications to how the function name is presented.
   */
  private class ComposableFunctionPresentation(private val function: KtFunction) :
    KotlinFunctionPresentation(function) {
    override fun getPresentableText(): String {
      return buildString {
        append("@Composable")
        function.name?.let { append(" $it") }

        function.getComposableFunctionRenderParts()?.let { parts ->
          parts.parameters?.let { append(it) }
          parts.tail?.let { append(" $it") }
        }
      }
    }
  }
}
