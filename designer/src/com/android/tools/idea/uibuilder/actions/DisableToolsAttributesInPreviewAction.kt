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
import com.android.tools.idea.flags.StudioFlags.NELE_TOGGLE_TOOLS_ATTRIBUTES_IN_PREVIEW
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons

/**
 * [ToggleAction] to enable or disable using 'tools' namespaced attributes in a Layout file preview.
 *
 * Default state (not-selected) means 'tools' namespaced attributes are enabled in the Layout file preview.
 */
object DisableToolsAttributesInPreviewAction : ToggleAction(
  "Disable Tools Attributes",
  "Disable or Enable 'tools' attributes in the Layout preview.",
  // TODO(146151278): Use the correct icon once it's uploaded, it should be a crossed out version of the tools_attribute icon.
  StudioIcons.LayoutEditor.Properties.TOOLS_ATTRIBUTE) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = NELE_TOGGLE_TOOLS_ATTRIBUTES_IN_PREVIEW.get() && e.getPreviewHandler() != null
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return e.getPreviewHandler()?.let {
      !it.previewWithToolsAttributes
    } ?: true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    // state == true means that the action is enabled, which in this case means disable the tools attributes in preview.
    e.getPreviewHandler()?.previewWithToolsAttributes = !state
  }

  private fun AnActionEvent.getPreviewHandler(): LayoutPreviewHandler? = this.getData(LAYOUT_PREVIEW_HANDLER_KEY)
}