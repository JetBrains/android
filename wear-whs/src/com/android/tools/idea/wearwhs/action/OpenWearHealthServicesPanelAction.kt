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
package com.android.tools.idea.wearwhs.action

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EmulatorId
import com.android.tools.idea.streaming.emulator.actions.AbstractEmulatorAction
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.view.WearHealthServicesToolWindow
import com.android.tools.idea.wearwhs.widget.WearHealthServicesToolWindowFactory
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.android.sdklib.deviceprovisioner.DeviceType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Opens the Wear Health Services Tool Window
 */
class OpenWearHealthServicesPanelAction : AbstractEmulatorAction(configFilter = { it.deviceType == DeviceType.WEAR && it.api >= 33 }) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    if (!StudioFlags.SYNTHETIC_HAL_PANEL.get()) {
      event.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val emulatorId = e.getData(EMULATOR_VIEW_KEY)?.emulator?.emulatorId
    if (project != null) {
      showWearHealthServicesToolWindow(project, emulatorId)
    }
  }

  private fun showWearHealthServicesToolWindow(project: Project, emulatorId: EmulatorId?) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(WearHealthServicesToolWindowFactory.ID) ?: return
    if (emulatorId != null) {
      (toolWindow.contentManager.contents[0].component as WearHealthServicesToolWindow).setSerialNumber(emulatorId.serialNumber)
      toolWindow.stripeTitle = message("wear.whs.panel.title.with.emulator.name").format(emulatorId.avdName)
    }
    toolWindow.show()
  }
}
