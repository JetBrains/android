/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.compose.preview.isAnyPreviewRefreshing
import com.android.tools.idea.compose.preview.message
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction

internal class ToggleAutoBuildOnSave :
  CheckboxAction(
    message("action.build.on.save.title"),
    message("action.build.on.save.description"), null) {
  override fun isSelected(e: AnActionEvent): Boolean = findComposePreviewManagersForContext(e.dataContext).any { it.isBuildOnSaveEnabled }

  override fun setSelected(e: AnActionEvent, enabled: Boolean) {
    findComposePreviewManagersForContext(e.dataContext).forEach { it.isBuildOnSaveEnabled = enabled }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = !isAnyPreviewRefreshing(e.dataContext)
  }
}