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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Action used to turn Layout Inspector on and off in Running Devices tool window.
 */
class ToggleLayoutInspectorAction : ToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return LayoutInspectorManager.getInstance(project).isEnabled
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    LayoutInspectorManager.getInstance(project).toggleLayoutInspector(state)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED.get()
  }
}