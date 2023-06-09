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
package com.android.tools.idea.layoutinspector.runningdevices.actions

import com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManager
import com.android.tools.idea.layoutinspector.runningdevices.RunningDevicesStateObserver
import com.android.tools.idea.layoutinspector.runningdevices.TabId
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Action used to turn Layout Inspector on and off in Running Devices tool window.
 */
class ToggleLayoutInspectorAction : ToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    val deviceSerialNumber = SERIAL_NUMBER_KEY.getData(e.dataContext) ?: return false

    return LayoutInspectorManager.getInstance(project).isEnabled(TabId(deviceSerialNumber))
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    val deviceSerialNumber = SERIAL_NUMBER_KEY.getData(e.dataContext) ?: return

    LayoutInspectorManager.getInstance(project).enableLayoutInspector(TabId(deviceSerialNumber), state)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val isEnabled = LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled
    e.presentation.isVisible = isEnabled

    val project = e.project ?: return
    RunningDevicesStateObserver.getInstance(project).update(isEnabled)
  }
}
