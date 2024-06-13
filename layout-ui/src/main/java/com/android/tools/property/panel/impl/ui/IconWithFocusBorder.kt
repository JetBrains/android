/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.property.panel.impl.ui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.property.panel.impl.support.ImageFocusListener
import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.ui.components.JBLabel
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlin.properties.Delegates

/**
 * A component to show an icon with a focus border.
 *
 * Specify [actionToPerform] when clicked or activated via the keyboard.
 */
open class IconWithFocusBorder(private val actionToPerform: () -> AnAction?) :
  JBLabel(), DataProvider {

  init {
    background = secondaryPanelBackground
    isFocusable = false
    @Suppress("LeakingThis") super.addFocusListener(ImageFocusListener(this))
    registerActionKey({ iconClicked(null) }, KeyStrokes.SPACE, "space")
    registerActionKey({ iconClicked(null) }, KeyStrokes.ENTER, "enter")
    super.addMouseListener(
      object : MouseAdapter() {
        override fun mousePressed(event: MouseEvent) {
          iconClicked(event)
        }
      }
    )
  }

  var readOnly by Delegates.observable(false) { _, _, _ -> updateFocusability() }

  override fun setIcon(icon: Icon?) {
    super.setIcon(icon)
    updateFocusability()
  }

  private fun updateFocusability() {
    isFocusable = icon != null && actionToPerform() != null && !readOnly
  }

  private fun iconClicked(mouseEvent: MouseEvent?) {
    if (readOnly) {
      return
    }
    val action = actionToPerform() ?: return
    if (action is ActionGroup) {
      val popupMenu =
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, action)
      val location = locationFromEvent(mouseEvent)
      popupMenu.component.show(this, location.x, location.y)
    } else {
      val event =
        AnActionEvent.createFromAnAction(
          action,
          mouseEvent,
          ActionPlaces.UNKNOWN,
          DataManager.getInstance().getDataContext(this),
        )
      action.actionPerformed(event)
    }
  }

  private fun locationFromEvent(mouseEvent: MouseEvent?): Point {
    if (mouseEvent != null) {
      return mouseEvent.locationOnScreen
    }
    val location = locationOnScreen
    return Point(location.x + width / 2, location.y + height / 2)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (hasFocus() && g is Graphics2D) {
      DarculaUIUtil.paintFocusBorder(g, width, height, 0f, true)
    }
  }

  override fun getData(dataId: String): Any? {
    return (parent as? DataProvider)?.getData(dataId)
  }
}
