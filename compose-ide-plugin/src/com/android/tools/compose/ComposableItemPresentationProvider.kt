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

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationProvider
import org.jetbrains.kotlin.idea.presentation.KotlinFunctionPresentation
import org.jetbrains.kotlin.idea.presentation.KtFunctionPresenter
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtFunction

/**
 * [ItemPresentationProvider] which overrides default behavior for displaying a method in a menu (eg, in "Add Imports").
 *
 * Composable methods will be displayed as "@Composable FunctionName". Other functions will maintain default display text (ie,
 * "functionName(arg1, arg2)".
 *
 * This implementation only modifies composable functions, and otherwise defers to the standard Kotlin presenter (implemented at
 * [KtFunctionPresenter]. Ideally we could just return null here for non-composable functions and the infrastructure would then go to the
 * next presenter; but the way the extension point is implemented means that it only runs the first presenter that's registered. So this
 * class must directly call into the KtFunctionPresenter that it's replacing, or else the overall default presentation for all elements
 * (which is not ideal for functions) will be used.
 *
 * This structure is sufficient for now, since this is the only ItemPresentationProvider for [KtFunction] that we have at the moment. If we
 * end up needing to implement other providers for [KtFunction] in the future, then we should re-evaluate whether the extension point needs
 * to be changed.
 */
class ComposableItemPresentationProvider : ItemPresentationProvider<KtFunction> {
  private val ktFunctionPresenter = KtFunctionPresenter()

  override fun getPresentation(function: KtFunction): ItemPresentation? {
    if (function.name?.isNotEmpty() == true && function.descriptor?.hasComposableAnnotation() == true) {
      return ComposableFunctionPresentation(function)
    }

    // Defer to standard Kotlin function presenter for non-compose functions.
    return ktFunctionPresenter.getPresentation(function)
  }

  /**
   * Presentation for composable functions. Based on the default [KotlinFunctionPresentation], with modifications to how the function name
   * is presented.
   * */
  private class ComposableFunctionPresentation(private val function: KtFunction) : KotlinFunctionPresentation(function) {
    override fun getPresentableText() = "@Composable ${function.name}"
  }
}
