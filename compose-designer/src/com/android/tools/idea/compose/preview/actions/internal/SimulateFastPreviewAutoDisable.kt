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
package com.android.tools.idea.compose.preview.actions.internal

import com.android.tools.idea.editors.fast.DisableReason
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/** Action that simulates that the preview has been automatically disabled because of an error. */
@Suppress("ComponentNotRegistered") // Registered in compose-designer.xml
class SimulateFastPreviewAutoDisable : AnAction("Simulate Fast Preview Auto-Disable") {
  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val fastPreviewManager = project.fastPreviewManager

    e.presentation.isEnabledAndVisible = fastPreviewManager.isEnabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val fastPreviewManager = project.fastPreviewManager

    fastPreviewManager.disable(
      DisableReason("Auto-Disabled", "Preview has been automatically disabled")
    )
  }
}
