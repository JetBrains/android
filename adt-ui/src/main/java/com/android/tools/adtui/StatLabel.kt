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
package com.android.tools.adtui

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.clickableTextColor
import com.android.tools.adtui.model.formatter.NumberFormatter
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.components.JBLabel
import java.awt.Font
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.font.TextAttribute
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * This class draws a properly formatted number with a description and optional icon
 */
class StatLabel @JvmOverloads constructor (num: Long,
                                           desc: String,
                                           numFont: Font = AdtUiUtils.DEFAULT_FONT,
                                           descFont: Font = AdtUiUtils.DEFAULT_FONT,
                                           private val action: Runnable? = null) : JPanel() {
  private val numLabel = JBLabel().apply { font = numFont }
  private val descLabel = JBLabel(desc).apply { font = descFont }

  var intContent: Long = num
    set(newInt) {
      numLabel.text = NumberFormatter.formatInteger(newInt)
    }

  var icon: Icon?
    get() = numLabel.icon
    set(newIcon) {
      numLabel.icon = newIcon
    }

  init {
    intContent = num
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    numLabel.horizontalTextPosition = SwingConstants.LEFT
    add(numLabel)
    add(descLabel)

    // If there is an associated action, visually indicate so
    if(action != null) {
      numLabel.foreground = clickableTextColor
      descLabel.foreground = clickableTextColor
      val (numOff, numOn) = makeUnderlinedFontSwitchers(numLabel)
      val (descOff, descOn) = makeUnderlinedFontSwitchers(descLabel)
      addMouseListener(object : MouseListener {
        override fun mouseEntered(e: MouseEvent?) {
          numOn()
          descOn()
        }
        override fun mouseExited(e: MouseEvent?) {
          numOff()
          descOff()
        }
        override fun mouseClicked(e: MouseEvent?) = action.run()
        override fun mousePressed(e: MouseEvent?) { }
        override fun mouseReleased(e: MouseEvent?) { }
      })
    }
  }

  @VisibleForTesting
  fun getNumText() = numLabel.text
}

/**
 * Returns a pair of callbacks for changing and restoring the label's font
 */
private fun makeUnderlinedFontSwitchers(label: JLabel): Pair<() -> Unit, () -> Unit> {
  val oldFont = label.font
  val newFont = oldFont.deriveFont(oldFont.attributes + (TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON))
  return Pair({ label.font = oldFont },
              { label.font = newFont })
}