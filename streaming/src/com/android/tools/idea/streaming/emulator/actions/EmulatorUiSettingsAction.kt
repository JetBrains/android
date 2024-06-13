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
package com.android.tools.idea.streaming.emulator.actions

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EmulatorUiSettingsController
import com.android.tools.idea.streaming.emulator.isReadyForAdbCommands
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.android.tools.idea.streaming.uisettings.ui.showUiSettingsPopup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.launch
import java.awt.EventQueue

private val isSettingsPickerEnabled: Boolean
  get() = StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.get()

/**
 * Opens a picker with UI settings of an emulator.
 */
internal class EmulatorUiSettingsAction : AbstractEmulatorAction(configFilter = { it.api >= 33 && isSettingsPickerEnabled }) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun isEnabled(event: AnActionEvent): Boolean {
    return super.isEnabled(event) && isReadyForAdbCommands(event)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorView = getEmulatorView(event) ?: return
    val project = event.project ?: return
    val serialNumber = getEmulatorController(event)?.emulatorId?.serialNumber ?: return
    val config = getEmulatorConfig(event) ?: return
    val model = UiSettingsModel(config.displaySize, config.density, config.api, config.deviceType)
    val controller = EmulatorUiSettingsController(project, serialNumber, model, config, emulatorView)
    AndroidCoroutineScope(emulatorView).launch {
      controller.populateModel()
      EventQueue.invokeLater {
        val panel = UiSettingsPanel(model, config.deviceType)
        showUiSettingsPopup(panel, this@EmulatorUiSettingsAction, event, emulatorView)
      }
    }
  }

  private fun isReadyForAdbCommands(event: AnActionEvent): Boolean {
    getEmulatorView(event) ?: return false
    getEmulatorConfig(event) ?: return false
    val project = event.project ?: return false
    val controller = getEmulatorController(event) ?: return false
    return isReadyForAdbCommands(project, controller.emulatorId.serialNumber)
  }
}
