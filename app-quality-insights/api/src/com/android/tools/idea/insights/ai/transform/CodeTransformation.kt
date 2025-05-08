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
package com.android.tools.idea.insights.ai.transform

import com.android.tools.idea.insights.ai.FixSuggester
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Represents a single transformation instance. */
interface CodeTransformation : Disposable {
  /**
   * Starts the transformation process. In the IDE, this brings up a diff view in which users can
   * accept suggested changes.
   */
  fun apply(): Flow<TransformDiffViewerEvent>
}

object NoopTransformation : CodeTransformation {
  override fun apply() = emptyFlow<TransformDiffViewerEvent>()

  override fun dispose() {}
}

class CodeTransformationImpl(
  private val project: Project,
  val instruction: String,
  val files: List<VirtualFile>,
) : CodeTransformation {
  override fun apply(): Flow<TransformDiffViewerEvent> {
    if (files.isEmpty()) {
      throw IllegalStateException("Should not call suggestFix on with no target files")
    }
    return (FixSuggester.Companion.EP_NAME.extensionList.firstOrNull()
        ?: throw IllegalStateException("Cannot find FixSuggester extension point"))
      .suggestFix(project, instruction, files, this)
  }

  override fun dispose() {}
}
