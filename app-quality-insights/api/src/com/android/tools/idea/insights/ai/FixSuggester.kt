/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.insights.ai.transform.TransformDiffViewerEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.Flow

interface FixSuggester {
  /**
   * Suggests code changes to the [files] based on the instructions contained in [insight].
   *
   * Returns a flow so callers can listen to events of this fix application.
   */
  fun suggestFix(
    project: Project,
    insight: String,
    files: List<VirtualFile>,
    parentDisposable: Disposable,
  ): Flow<TransformDiffViewerEvent>

  companion object {
    val EP_NAME: ExtensionPointName<FixSuggester> =
      ExtensionPointName.create("com.android.tools.idea.insights.ai.fixSuggester")
  }
}
