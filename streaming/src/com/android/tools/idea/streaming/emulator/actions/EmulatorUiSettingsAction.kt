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
import com.android.tools.idea.streaming.core.findComponentForAction
import com.android.tools.idea.streaming.emulator.EmulatorUiSettingsController
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.launch
import java.awt.EventQueue
import javax.swing.JComponent

private val isSettingsPickerEnabled: Boolean
  get() = StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.get()

/**
 * Opens a picker with UI settings of an emulator.
 */
internal class EmulatorUiSettingsAction : AbstractEmulatorAction(configFilter = { it.api >= 34 && isSettingsPickerEnabled }) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorView = getEmulatorView(event) ?: return
    val component = event.findComponentForAction(this) as? JComponent ?: emulatorView
    val project = event.project ?: return
    val serialNumber = getEmulatorController(event)?.emulatorId?.serialNumber ?: return
    val disposable = Disposer.newDisposable()
    val model = UiSettingsModel()
    val controller = EmulatorUiSettingsController(project, serialNumber, model, disposable)
    val balloon = UiSettingsPanel(model, disposable).createPicker()
    Disposer.register(balloon, disposable)
    AndroidCoroutineScope(emulatorView).launch {
      controller.populateModel()
      EventQueue.invokeLater {
        balloon.show(RelativePoint.getCenterOf(component), Balloon.Position.above)
      }
    }
  }
}
