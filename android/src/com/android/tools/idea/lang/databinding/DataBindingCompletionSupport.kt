/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("DataBindingCompletionUtil")

package com.android.tools.idea.lang.databinding

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Extension point interface for providing utilities related to code completion.
 */
interface DataBindingCompletionSupport {
  /**
   * Used to suggest completions related to data-binding. This is used in the expressions ({@code @{...}}) and in the {@code <data>} tag.
   * Should be a no-op if no data binding plugin is enabled, or if any other conditions arise that makes completion impossible.
   */
  fun addCompletions(params: CompletionParameters, resultSet: CompletionResultSet)
}

/**
 * Calls the first plugin's [DataBindingCompletionSupport.addCompletions].
 * No-op if no extension point implementation is found.
 */
fun addCompletions(params: CompletionParameters, resultSet: CompletionResultSet) {
  val extensionPoint: ExtensionPointName<DataBindingCompletionSupport> = ExtensionPointName(
    "com.android.tools.idea.lang.databinding.dataBindingCompletionSupport")
  extensionPoint.extensionList.first()?.addCompletions(params, resultSet)
}