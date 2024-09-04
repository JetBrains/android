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
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.core.findComponentForAction
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.streaming.emulator.actions.AbstractEmulatorAction
import com.android.tools.idea.streaming.emulator.isReadyForAdbCommands
import com.android.tools.idea.streaming.uisettings.ui.findRelativePoint
import com.android.tools.idea.wearwhs.view.WearHealthServicesPanelManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlinx.coroutines.launch

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
    val emulatorView = EMULATOR_VIEW_KEY.getData(event.dataContext) ?: return false
    val project = event.project ?: return false
    return isReadyForAdbCommands(project, emulatorView.deviceSerialNumber)
  }

  private fun showWearHealthServicesToolPopup(action: AnAction, event: AnActionEvent) {
    val emulatorView = EMULATOR_VIEW_KEY.getData(event.dataContext) ?: return
    val project = event.project ?: return

    val panel = project.service<WearHealthServicesPanelManager>().getOrCreate(emulatorView.emulator)

    val balloon =
      JBPopupFactory.getInstance()
        .createBalloonBuilder(panel.component)
        .setShadow(true)
        .setHideOnAction(false)
        .setBlockClicksThroughBalloon(true)
        .setRequestFocus(true)
        .setAnimationCycle(200)
        .setFillColor(secondaryPanelBackground)
        .setBorderColor(secondaryPanelBackground)
        .createBalloon()

    AndroidCoroutineScope(balloon).launch {
      panel.onUserApplyChangesFlow.collect { balloon.hide() }
    }

    // Show the UI settings popup relative to the ActionButton.
    // If such a component is not found use the displayView. The action was likely activated from
    // the keyboard.
    val component = event.findComponentForAction(action) as? JComponent ?: emulatorView
    val position = findRelativePoint(component, emulatorView)

    // Hide the balloon if Studio looses focus:
    val window = SwingUtilities.windowForComponent(position.component)
    if (window != null) {
      val listener =
        object : WindowAdapter() {
          override fun windowLostFocus(event: WindowEvent) {
            balloon.hide()
          }
        }
      window.addWindowFocusListener(listener)
      Disposer.register(balloon) { window.removeWindowFocusListener(listener) }
    }

    // Hide the balloon when the device window closes:
    Disposer.register(emulatorView, balloon)

    // Show the balloon above the component if there is room, otherwise below:
    balloon.show(position, Balloon.Position.above)
  }
}
