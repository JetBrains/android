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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons

/** Action to simulate a back press while in interactive preview. */
class BackNavigationAction() :
  DumbAwareAction(message("action.navigate.back"), null, StudioIcons.Emulator.Toolbar.BACK) {

  override fun actionPerformed(e: AnActionEvent) {
    val selectedPreview =
      e.dataContext.findPreviewManager(PreviewModeManager.KEY)?.mode?.value?.selected ?: return
    val backPressDispatcher = selectedPreview.backPressedDispatcher ?: return
    backPressDispatcher::class
      .java
      .declaredMethods
      .single { it.name == "onBackPressed" }
      .invoke(backPressDispatcher)
  }

  /** BGT is needed when calling [findPreviewManager] because it accesses the VirtualFile */
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
