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

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.core.findComponentForAction
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.actions.AbstractEmulatorAction
import com.android.tools.idea.streaming.emulator.isReadyForAdbCommands
import com.android.tools.idea.wearwhs.communication.ContentProviderDeviceManager
import com.android.tools.idea.wearwhs.view.WearHealthServicesPanelController
import com.android.tools.idea.wearwhs.view.WearHealthServicesStateManagerImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.ui.awt.RelativePoint
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope

private val PANEL_CONTROLLER_KEY =
  Key.create<WearHealthServicesPanelController>("WearHealthServicesPanelController")

/** Opens the Wear Health Services Tool Window */
class OpenWearHealthServicesPanelAction :
  AbstractEmulatorAction(
    configFilter = {
      StudioFlags.WEAR_HEALTH_SERVICES_PANEL.get() &&
        it.deviceType == DeviceType.WEAR &&
        it.api >= 33
    }
  ) {

  override fun isEnabled(event: AnActionEvent): Boolean {
    return super.isEnabled(event) && isReadyForAdbCommands(event)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    showWearHealthServicesToolPopup(this@OpenWearHealthServicesPanelAction, e)
  }

  private fun isReadyForAdbCommands(event: AnActionEvent): Boolean {
    val deviceSerialNumber = event.getData(DISPLAY_VIEW_KEY)?.deviceSerialNumber ?: return false
    val project = event.project ?: return false
    return isReadyForAdbCommands(project, deviceSerialNumber)
  }

  private fun showWearHealthServicesToolPopup(action: AnAction, event: AnActionEvent) {
    val project = event.project ?: return
    val emulatorController = event.getData(EMULATOR_CONTROLLER_KEY) ?: return
    val displayView = event.getData(DISPLAY_VIEW_KEY) ?: return

    val panelController =
      emulatorController.getOrCreateUserData(PANEL_CONTROLLER_KEY) {
        val workerScope: CoroutineScope = AndroidCoroutineScope(emulatorController, workerThread)
        val uiScope: CoroutineScope = AndroidCoroutineScope(emulatorController, uiThread)
        val adbSessionProvider = { AdbLibService.getSession(project) }
        val serialNumber = displayView.deviceSerialNumber
        val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
        val stateManager =
          WearHealthServicesStateManagerImpl(deviceManager, workerScope = workerScope).also {
            Disposer.register(emulatorController, it)
            it.serialNumber = serialNumber
          }
        WearHealthServicesPanelController(
          stateManager = stateManager,
          workerScope = workerScope,
          uiScope = uiScope,
        )
      }

    // Show the UI settings popup relative to the ActionButton.
    // If such a component is not found use the displayView. The action was likely activated from
    // the keyboard.
    val component = event.findComponentForAction(action) as? JComponent ?: displayView
    val position = findRelativePoint(component, displayView)

    panelController.showWearHealthServicesToolPopup(displayView, position)
  }
}

/**
 * Returns the point for displaying the balloon.
 * - If [component] is a DeviceView or EmulatorView (ex: when action is invoked from the keyboard)
 *   returns the point NW of the [component]
 * - If [component] is in a popup itself, converts the point relative to the [displayView]
 * - Otherwise, returns the center of the button that was pressed
 */
fun findRelativePoint(component: JComponent, displayView: AbstractDisplayView): RelativePoint {
  return when {
    component is AbstractDisplayView -> RelativePoint.getNorthWestOf(component)
    PopupUtil.getPopupContainerFor(component) != null ->
      RelativePoint.getCenterOf(component).getPointOn(displayView)
    else -> RelativePoint.getCenterOf(component)
  }
}
