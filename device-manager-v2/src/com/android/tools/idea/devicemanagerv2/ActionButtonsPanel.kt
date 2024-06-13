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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.tools.adtui.categorytable.IconButton
import com.android.tools.adtui.categorytable.constrainSize
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBDimension
import javax.swing.BoxLayout
import kotlinx.coroutines.CoroutineScope

internal open class ActionButtonsPanel : JBPanel<ActionButtonsPanel>() {
  protected fun setUp(vararg buttons: IconButton?) {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    isOpaque = false

    val size = JBDimension(22, 22)
    for (button in buttons.filterNotNull()) {
      button.constrainSize(size)
      add(button)
    }
  }

  open fun updateState(state: DeviceRowData) {}
}

internal class DeviceHandleButtonsPanel(val project: Project?, handle: DeviceHandle) :
  ActionButtonsPanel() {

  private val overflowButton = OverflowButton()

  init {
    val activationAction = handle.activationAction
    val deactivationAction = handle.deactivationAction
    when {
      activationAction != null && deactivationAction != null -> {
        setUp(
          StartStopButton(handle, activationAction, deactivationAction, handle.repairDeviceAction),
          overflowButton,
        )
      }
      project == null -> setUp(overflowButton)
      else -> setUp(StartStopMirroringButton(handle, project), overflowButton)
    }
  }
}

internal class DeviceTemplateButtonsPanel(
  coroutineScope: CoroutineScope,
  deviceTemplate: DeviceTemplate,
) : ActionButtonsPanel() {

  init {
    setUp(ActivateTemplateButton(coroutineScope, deviceTemplate), OverflowButton())
  }
}
