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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.classloading.ConstantRemapper
import com.android.tools.idea.rendering.classloading.ConstantRemapperManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Debug action to enable live literals tracking in the Compose Editor.
 */
internal class LiveLiteralsAction : ToggleAction("Live literals tracking") {
  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isVisible = findComposePreviewManagersForContext(e.dataContext).any { it.hasLiveLiterals }
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    findComposePreviewManagersForContext(e.dataContext).any { it.isLiveLiteralsEnabled }

  override fun setSelected(e: AnActionEvent, state: Boolean) =
    findComposePreviewManagersForContext(e.dataContext).forEach { it.isLiveLiteralsEnabled = state }
}