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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.findComponentForAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Shows the UI settings popup relative to the ActionButton if possible.
 */
internal fun showUiSettingsPopup(panel: JComponent, action: AnAction, event: AnActionEvent, displayView: AbstractDisplayView) {
  val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(panel)
      .setShadow(true)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(200)
      .setFillColor(secondaryPanelBackground)
      .createBalloon()

  // Show the UI settings popup relative to the ActionButton.
  // If such a component is not found use the displayView. The action was likely activated from the keyboard.
  val component = event.findComponentForAction(action) as? JComponent ?: displayView
  val position = findRelativePoint(component, displayView)

  // Hide the balloon if Studio looses focus.
  val window = SwingUtilities.windowForComponent(position.component)
  if (window != null) {
    val listener = object : WindowAdapter() {
      override fun windowLostFocus(event: WindowEvent) {
        balloon.hide()
      }
    }
    window.addWindowFocusListener(listener)
    Disposer.register(balloon) { window.removeWindowFocusListener(listener) }
  }

  // Hide the balloon when the device window closes.
  Disposer.register(displayView, balloon)

  // Show the balloon above the component if there is room, otherwise below.
  balloon.show(position, Balloon.Position.above)
}

/**
 * Returns the point for displaying the balloon.
 * - If [component] is a DeviceView or EmulatorView (ex: when action is invoked from the keyboard) returns the point NW of the [component]
 * - If [component] is in a popup itself, converts the point relative to the [displayView]
 * - Otherwise, returns the center of the button that was pressed
 */
fun findRelativePoint(component: JComponent, displayView: AbstractDisplayView): RelativePoint {
  return when {
    component is AbstractDisplayView -> RelativePoint.getNorthWestOf(component)
    PopupUtil.getPopupContainerFor(component) != null -> RelativePoint.getCenterOf(component).getPointOn(displayView)
    else -> RelativePoint.getCenterOf(component)
  }
}
