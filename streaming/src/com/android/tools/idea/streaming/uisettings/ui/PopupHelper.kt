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
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.BalloonImpl
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.awt.AnchoredPoint
import java.awt.Component
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Shows the UI settings popup relative to the ActionButton if possible.
 */
internal fun showUiSettingsPopup(panel: JComponent, displayView: AbstractDisplayView) {
  val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(panel)
      .setShadow(true)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setRequestFocus(true)
      .setShowCallout(false)
      .setAnimationCycle(200)
      .setFillColor(secondaryPanelBackground)
      .setBorderColor(secondaryPanelBackground)
      .createBalloon()

  // Show the UI settings popup relative to the DisplayView.
  val position = AnchoredPoint(AnchoredPoint.Anchor.LEFT, displayView)

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

  // Show the balloon anchored to the left of the DisplayView.
  balloon.show(position, Balloon.Position.atLeft)

  // Make the Balloon moveable.
  object : WindowMoveListener(panel) {
    override fun getView(component: Component?): Component? {
      return (balloon as? BalloonImpl)?.component ?: panel
    }
  }.installTo(panel)
}
