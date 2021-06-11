/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.profilers

import java.awt.Point
import java.awt.Toolkit
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame

object DropDownButton {

  /**
   * Creates a button that opens to a custom drop-down UI
   */
  @JvmStatic
  fun of(text: String, makeUi: () -> JComponent) = JButton(text).apply {
    addActionListener {
      val dialog = JDialog(JFrame())
      val button = this
      with(dialog) {
        isUndecorated = true
        add(makeUi())
        pack()
        location = Toolkit.getDefaultToolkit().let {
          val screenSize = it.screenSize
          val screenInsets = it.getScreenInsets(graphicsConfiguration)
          val buttonX = button.locationOnScreen.x
          val buttonY = button.locationOnScreen.y
          val x = when { // align with left edge if possible, but resort to right edge
            buttonX + width <= screenSize.width - screenInsets.right -> buttonX
            else -> buttonX + button.width - width
          }
          val y = when { // drop down if possible, but resort to up
            buttonY + button.height + height <= screenSize.height - screenInsets.bottom -> buttonY + button.height
            else -> buttonY - height
          }
          Point(x, y)
        }
        addWindowFocusListener(object : WindowFocusListener {
          override fun windowLostFocus(e: WindowEvent) = dialog.dispose()
          override fun windowGainedFocus(e: WindowEvent) {}
        })
        isVisible = true
      }
    }
  }
}