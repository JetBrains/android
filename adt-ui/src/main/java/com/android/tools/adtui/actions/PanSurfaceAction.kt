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
package com.android.tools.adtui.actions

import com.android.tools.adtui.PANNABLE_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons.LayoutEditor.Toolbar.PAN_TOOL
import icons.StudioIcons.LayoutEditor.Toolbar.PAN_TOOL_SELECTED

object PanSurfaceAction : ToggleAction("Pan screen (hold SPACE bar and drag)", "Click and drag the surface.", PAN_TOOL) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    val pannable = event.getData(PANNABLE_KEY)
    event.presentation.isEnabled = pannable?.isPannable == true
    // setSelectedIcon doesn't work as expected, so instead we manually change the regular Icon when the Toggle is Selected
    event.presentation.icon = if (isSelected(event)) PAN_TOOL_SELECTED else PAN_TOOL
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    event.getData(PANNABLE_KEY)?.let { pannable -> pannable.isPanning = state }
  }

  override fun isSelected(event: AnActionEvent): Boolean {
    val pannable = event.getData(PANNABLE_KEY)
    return pannable?.isPanning ?: false
  }
}
