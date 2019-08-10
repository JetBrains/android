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
package com.android.tools.adtui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.components.JBLabel
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlin.properties.Delegates

/**
 * A component to show an icon with a focus border.
 *
 * Set [onClick] to trigger an action when the Icon is clicked or the Space/Enter keys are pressed while focusing the icon.
 */
class FocusableIcon : JBLabel() {

  var onClick: (() -> Unit)? = null

  private val focusListener: FocusListener = object: FocusListener {
    override fun focusLost(e: FocusEvent?) {
      repaint()
    }

    override fun focusGained(e: FocusEvent?) {
      repaint()
    }
  }

  init {
    background = secondaryPanelBackground
    isFocusable = true
    registerActionKey({ iconClicked() }, KeyStrokes.SPACE, "space")
    registerActionKey({ iconClicked() }, KeyStrokes.ENTER, "enter")
    super.addFocusListener(focusListener)
    super.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        iconClicked()
      }
    })
  }

  var readOnly by Delegates.observable(false) { _, _, _ -> updateFocusability() }

  override fun setIcon(icon: Icon?) {
    super.setIcon(icon)
    updateFocusability()
  }

  private fun updateFocusability() {
    isFocusable = icon != null && !readOnly
  }

  private fun iconClicked() {
    if (readOnly) {
      return
    }
    onClick?.invoke()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (isFocusOwner && g is Graphics2D) {
      DarculaUIUtil.paintFocusBorder(g, width, height, 0f, true)
    }
  }
}