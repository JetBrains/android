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

import com.android.tools.idea.compose.preview.ComposePreviewManagerEx
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

private const val SHOW = "Show"
private const val HIDE = "Hide"

/** Action that controls when to enable the debug boundaries mode mode. */
internal class ShowDebugBoundaries : ToggleAction("${SHOW} Composable Bounds", null, null) {

  override fun isSelected(e: AnActionEvent): Boolean =
    findComposePreviewManagersForContext(e.dataContext)
      .filterIsInstance<ComposePreviewManagerEx>()
      .any { it.showDebugBoundaries }

  override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
    findComposePreviewManagersForContext(e.dataContext)
      .filterIsInstance<ComposePreviewManagerEx>()
      .forEach { it.showDebugBoundaries = isSelected }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.text =
      if (isSelected(e)) {
        "${HIDE} Composable Bounds"
      } else {
        "${SHOW} Composable Bounds"
      }
  }

  override fun displayTextInToolbar(): Boolean = true
}
