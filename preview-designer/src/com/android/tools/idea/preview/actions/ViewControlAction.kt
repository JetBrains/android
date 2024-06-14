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
package com.android.tools.idea.preview.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

open class ViewControlAction(
  private val isEnabled: (e: AnActionEvent) -> Boolean,
  private val essentialModeDescription: String =
    message("action.scene.view.control.essentials.mode.description"),
) :
  DropDownAction(
    message("action.scene.view.control.title"),
    message("action.scene.view.control.description"),
    AllIcons.Debugger.RestoreLayout,
  ) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = isEnabled(e)
    e.presentation.description =
      if (PreviewEssentialsModeManager.isEssentialsModeEnabled) essentialModeDescription
      else message("action.scene.view.control.description")
  }

  // Actions calling isDisabled in the update method, must run in BGT
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
