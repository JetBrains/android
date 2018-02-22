/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui

import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.Point
import javax.swing.JComponent

/**
 * A popup balloon that appears on above a given location with an arrow pointing this location.
 *
 * The popup is automatically dismissed when the user clicks outside.
 */
class LightCalloutPopup(
  val closedCallback: (() -> Unit)? = null,
  val cancelCallBack: (() -> Unit)? = null,
  val beforeShownCallback: (() -> Unit)? = null
) {

  private var balloon: Balloon? = null

  fun show(
    content: JComponent,
    parentComponent: JComponent,
    location: Point
  ) {

    // Let's cancel any previous balloon shown by this instance of ScenePopup
    if (balloon != null) {
      cancel()
    }

    balloon = createPopup(content).apply {
      addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent?) {
          beforeShownCallback?.invoke()
        }

        override fun onClosed(event: LightweightWindowEvent?) {
          if (event?.isOk == true) {
            closedCallback?.invoke()
          } else {
            cancelCallBack?.invoke()
          }
        }
      })
      show(RelativePoint(parentComponent, location), Balloon.Position.above)
    }
  }

  fun close() {
    balloon?.hide(true)
  }

  fun cancel() {
    balloon?.hide(false)
  }

  private fun createPopup(component: JComponent) =
    JBPopupFactory.getInstance().createBalloonBuilder(component)
      .setFillColor(JBColor.WHITE)
      .setBorderColor(JBColor.border())
      .setBorderInsets(JBUI.insets(2))
      .setAnimationCycle(Registry.intValue("ide.tooltip.animationCycle"))
      .setShowCallout(true)
      .setPositionChangeYShift(2)
      .setHideOnKeyOutside(false)
      .setHideOnAction(true)
      .setRequestFocus(true)
      .setDialogMode(false)
      .createBalloon()
}