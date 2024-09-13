/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ai

import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.studiobot.StudioBot
import com.intellij.openapi.project.Project

/** Exposes AI related tools to AQI. */
interface GeminiToolkit {
  val isGeminiEnabled: Boolean

  suspend fun getSource(
    stack: StacktraceGroup,
    contextSharingOverride: Boolean = false,
  ): CodeContextData
}

class GeminiToolkitImpl(private val project: Project) : GeminiToolkit {
  override val isGeminiEnabled: Boolean
    get() = StudioBot.getInstance().isAvailable()

  private val codeContextResolver: CodeContextResolver
    get() = CodeContextResolver.getInstance(project)

  override suspend fun getSource(
    stack: StacktraceGroup,
    contextSharingOverride: Boolean,
  ): CodeContextData {
    if (!StudioBot.getInstance().isContextAllowed(project) && !contextSharingOverride)
      return CodeContextData.EMPTY
    return codeContextResolver.getSource(stack)
  }
}
