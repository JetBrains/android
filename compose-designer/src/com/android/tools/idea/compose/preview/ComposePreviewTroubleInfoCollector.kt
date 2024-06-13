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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.preview.actions.getPreviewManager
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.troubleshooting.TroubleInfoCollector

private fun findAllComposePreviewManagers(project: Project): Collection<ComposePreviewManager> =
  FileEditorManager.getInstance(project)?.allEditors?.mapNotNull { it.getPreviewManager() }
    ?: emptyList()

private fun collectComposePreviewManagerInfo(project: Project): String =
  findAllComposePreviewManagers(project).joinToString("\n") {
    "ComposePreviewManager: status=${it.status()} mode=${it.mode}"
  }

/** [TroubleInfoCollector] to collect information related to Compose Preview. */
class ComposePreviewTroubleInfoCollector : TroubleInfoCollector {
  override fun collectInfo(project: Project) =
    """
Preview Essentials Mode enabled: ${PreviewEssentialsModeManager.isEssentialsModeEnabled}

${collectComposePreviewManagerInfo(project)}
    """
}
