/*
 * Copyright (C) 2025 The Android Open Source Project
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
/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingUtilities
import org.jetbrains.annotations.VisibleForTesting

private const val HORIZONTAL_MARGIN = 20
private const val VERTICAL_MARGIN = 8
private const val SEPARATOR_MARGIN = 4

/** Display the UiSettingsPopup */
internal fun showUiSettingsPopup(model: UiSettingsModel, deviceType: DeviceType, parentComponent: AbstractDisplayView) {
  showUiSettingsPopup(model, deviceType, parentComponent, parentComponent)
}

@VisibleForTesting
internal fun showUiSettingsPopup(
  model: UiSettingsModel,
  deviceType: DeviceType,
  parentComponent: JComponent,
  parentDisposable: Disposable,
) {
  if (parentComponent.isShowing) {
    val header = UiSettingsHeader(model)
    val panel = UiSettingsPanel(model, deviceType)
    val contentPanel = createUiSettingsContentPanel(header, panel)
    val popup =
      JBPopupFactory.getInstance()
        .createComponentPopupBuilder(contentPanel, panel)
        .setRequestFocus(true)
        .setFocusable(true)
        .setCancelOnClickOutside(true)
        .setCancelOnWindowDeactivation(true)
        .setCancelKeyEnabled(true)
        .setMovable(true)
        .createPopup()
    Disposer.register(parentDisposable, popup)

    // WindowMoveListener allows the window to be moved by dragging the panel.
    val moveListener = WindowMoveListener(contentPanel)
    moveListener.installTo(panel)
    moveListener.installTo(header)

    Disposer.register(popup) {
      moveListener.uninstallFrom(panel)
      moveListener.uninstallFrom(header)
    }

    popup.show(popup.leftOf(parentComponent))
  }
}

private fun createUiSettingsContentPanel(header: JComponent, panel: JComponent): JComponent {
  val contentPanel = JPanel(BorderLayout())
  header.border =
    JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
      JBUI.Borders.empty(VERTICAL_MARGIN, HORIZONTAL_MARGIN, SEPARATOR_MARGIN, HORIZONTAL_MARGIN),
    )
  panel.border = JBUI.Borders.empty(SEPARATOR_MARGIN, HORIZONTAL_MARGIN, VERTICAL_MARGIN, HORIZONTAL_MARGIN)

  contentPanel.add(header, BorderLayout.NORTH)
  contentPanel.add(panel, BorderLayout.CENTER)
  contentPanel.border = PopupBorder.Factory.create(true, true)

  contentPanel.isFocusCycleRoot = true
  contentPanel.isFocusTraversalPolicyProvider = true
  contentPanel.focusTraversalPolicy =
    object : LayoutFocusTraversalPolicy() {
      override fun getFirstComponent(container: Container): Component? {
        val first = super.getFirstComponent(container) ?: return null
        val from = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val fromOutside = from == null || !SwingUtilities.isDescendingFrom(from, container)
        return if (first.name == RESET_TITLE && fromOutside) {
          val after = super.getComponentAfter(container, first)
          // Sometimes getComponentAfter returns the container itself if focus CycleRoot is true
          if (after == container) first else after
        } else first
      }
    }

  return contentPanel
}

private fun JBPopup.leftOf(component: JComponent): RelativePoint {
  val screenLocation = component.locationOnScreen
  val popupY = screenLocation.y + JBUIScale.scale(10)
  val popupWidth = content.preferredSize.width
  val popupX = screenLocation.x - popupWidth + JBUIScale.scale(5)
  val desiredScreenPoint = Point(popupX, popupY)
  return RelativePoint(desiredScreenPoint)
}
