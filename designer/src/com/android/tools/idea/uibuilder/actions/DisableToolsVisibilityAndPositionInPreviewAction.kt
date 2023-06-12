/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.idea.actions.LAYOUT_PREVIEW_HANDLER_KEY
import com.android.tools.idea.actions.LayoutPreviewHandler
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons.LayoutEditor.Toolbar.TOOLS_ATTRIBUTE_OFF
import icons.StudioIcons.LayoutEditor.Toolbar.TOOLS_ATTRIBUTE_ON

/**
 * [ToggleAction] to enable or disable using 'tools' namespaced 'visibility' and 'layout_editor_absoluteX/Y' attributes in the Layout Editor
 * preview.
 *
 * Default state (not-selected) means 'visibility' and 'layout_editor_absoluteX/Y' tools attributes are enabled in the Layout file preview.
 */
object DisableToolsVisibilityAndPositionInPreviewAction : ToggleAction(
  "Toggle tools visibility and position",
  "Disable or Enable 'tools:visibility' and 'tools:layout_editor_absoluteX/Y' attributes in the Layout preview.",
  TOOLS_ATTRIBUTE_ON) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = true
    e.presentation.isEnabled = e.getPreviewHandler() != null
    e.presentation.icon = if (isSelected(e)) TOOLS_ATTRIBUTE_OFF else TOOLS_ATTRIBUTE_ON
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return e.getPreviewHandler()?.let {
      !it.previewWithToolsVisibilityAndPosition
    } ?: true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    // state == true means that the action is enabled, which in this case means disable the tools attributes in preview.
    e.getPreviewHandler()?.previewWithToolsVisibilityAndPosition = !state
  }

  private fun AnActionEvent.getPreviewHandler(): LayoutPreviewHandler? = this.getData(LAYOUT_PREVIEW_HANDLER_KEY)
}