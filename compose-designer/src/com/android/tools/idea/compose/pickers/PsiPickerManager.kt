/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers

import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.idea.compose.pickers.base.model.PsiPropertiesModel
import com.android.tools.idea.compose.pickers.base.model.PsiPropertyView
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.property.panel.api.PropertiesPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.JBUI
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.LayoutFocusTraversalPolicy

internal object PsiPickerManager {

  /**
   * Shows a picker for editing a [PsiPropertiesModel]s. The user can modify the model using this
   * dialog.
   *
   * @param location the location on screen from which the Picker popup will be shown
   * @param displayTitle Title displayed at the top of the Picker popup
   * @param model model used to drive the picker, defines how properties are edited and how the UI
   *   is built
   * @param balloonPosition position of the picker
   */
  fun show(
    location: Point,
    displayTitle: String,
    model: PsiPropertiesModel,
    balloonPosition: Balloon.Position = Balloon.Position.below
  ) {
    val tracker = model.tracker
    val disposable = Disposer.newDisposable()
    var popup: LightCalloutPopup? = null
    val closeHandler = PopupCloseHandler(ComponentUtil.getActiveWindow()) { popup?.close() }
    Toolkit.getDefaultToolkit().addAWTEventListener(closeHandler, AWTEvent.MOUSE_EVENT_MASK)
    val onClosedOrCancelled: () -> Unit = {
      Toolkit.getDefaultToolkit().removeAWTEventListener(closeHandler)
      Disposer.dispose(disposable)
      tracker.pickerClosed()
      ApplicationManager.getApplication().executeOnPooledThread(tracker::logUsageData)
    }
    popup =
      LightCalloutPopup(closedCallback = onClosedOrCancelled, cancelCallBack = onClosedOrCancelled)
    val pickerPanel = createPickerPanel(disposable, popup::close, displayTitle, model)

    tracker.pickerShown()
    popup.show(
      content = pickerPanel,
      parentComponent = null,
      location = location,
      position = balloonPosition,
      hideOnOutsideClick = false
    )
  }
}

private fun createPickerPanel(
  disposable: Disposable,
  closePopupCallBack: () -> Unit,
  displayTitle: String,
  model: PsiPropertiesModel
): JPanel {
  val propertiesPanel =
    PropertiesPanel<PsiPropertyItem>(disposable).also { it.addView(PsiPropertyView(model)) }

  return JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
    border = JBUI.Borders.empty(0, 4)
    add(JLabel(displayTitle).apply { border = JBUI.Borders.empty(8, 0) })
    add(JSeparator())
    add(
      propertiesPanel.component.apply {
        isOpaque = false
        border = JBUI.Borders.empty(0, 0, 8, 0)
      }
    )
    isFocusCycleRoot = true
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    registerActionKey(
      closePopupCallBack,
      KeyStrokes.ESCAPE,
      name = "close",
      condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    )
  }
}

/**
 * [AWTEventListener] that makes it so that the [LightCalloutPopup] is only closed when a click
 * happened outside of ANY popup.
 */
private class PopupCloseHandler(
  private val ownerWindow: Window,
  private val closePopupCallback: () -> Unit
) : AWTEventListener {
  override fun eventDispatched(event: AWTEvent?) {
    if (event is MouseEvent) {
      if (event.id != MouseEvent.MOUSE_PRESSED) return

      if (!inPopupOrBalloon(event.component) && !isWithinOriginalWindow(event)) {
        closePopupCallback()
      }
    }
  }

  private fun isWithinOriginalWindow(event: MouseEvent): Boolean {
    val owner: Component = ownerWindow
    var child: Component? = ComponentUtil.getWindow(event.component)
    if (child !== owner) {
      while (child != null) {
        if (child === owner) {
          return true
        }
        child = child.parent
      }
    }
    return false
  }

  private fun inPopupOrBalloon(component: Component): Boolean {
    // inclusive parent
    var parent = component
    while (parent is JComponent) {
      if (parent is JPopupMenu || parent.getClientProperty(Balloon.KEY) is Balloon) {
        return true
      }
      parent = parent.parent
    }
    return false
  }
}
