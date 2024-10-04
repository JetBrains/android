/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class WearHealthServicesPanelController(
  stateManager: WearHealthServicesStateManager,
  workerScope: CoroutineScope,
  uiScope: CoroutineScope,
) {

  private val panel =
    createWearHealthServicesPanel(stateManager, uiScope = uiScope, workerScope = workerScope)

  fun showWearHealthServicesToolPopup(parentDisposable: Disposable, position: RelativePoint) {
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
    AndroidCoroutineScope(balloon).launch {
      panel.onUserTriggerEventFlow.collect { balloon.hide() }
    }

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

    // Hide the balloon when the parentDisposable is disposed
    Disposer.register(parentDisposable, balloon)

    // Show the balloon above the component if there is room, otherwise below:
    balloon.show(position, Balloon.Position.above)
  }
}
