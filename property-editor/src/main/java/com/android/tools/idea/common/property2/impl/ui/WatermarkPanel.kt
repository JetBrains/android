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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.common.property2.api.Watermark
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.SwingConstants

private const val LABEL_SPACING = 16
private const val HORIZONTAL_MARGIN = 16
private const val FILL_WIDTH = 16

/**
 * A panel to show a watermark message.
 *
 * TODO: Add the ability to specify and show actions in the actionMessage.
 */
class WatermarkPanel: AdtSecondaryPanel() {
  private val messageLabel = JBLabel()
  private val actionMessageLabel = JBLabel()
  private val help = JBLabel()

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(createFiller())
    add(setupLabel(messageLabel))
    add(setupLabel(actionMessageLabel))
    add(setupLabel(help))
    add(createFiller())
    border = JBUI.Borders.empty(0, JBUI.scale(HORIZONTAL_MARGIN))
  }

  var model: Watermark? = null
    set(value) {
      field = value
      setLabelText(messageLabel, value?.message)
      setLabelText(actionMessageLabel, value?.actionMessage)
      setLabelText(help, value?.helpMessage)
    }

  private fun createFiller(): JComponent {
    val width = JBUI.scale(FILL_WIDTH)
    return Box.Filler(Dimension(width, 0), Dimension(width, Int.MAX_VALUE), Dimension(width, Int.MAX_VALUE))
  }

  private fun setupLabel(label: JBLabel): JBLabel {
    label.horizontalAlignment = SwingConstants.CENTER
    label.border = JBUI.Borders.emptyBottom(JBUI.scale(LABEL_SPACING))
    label.foreground = JBColor(0x999999, 0x999999)
    label.isVisible = label.text.isNotEmpty()
    return label
  }

  private fun setLabelText(label: JBLabel, value: String?) {
    label.text = asHtmlForWrapping(value)
    label.isVisible = label.text.isNotEmpty()
  }

  private fun asHtmlForWrapping(text: String?): String {
    return if (text != null) "<html><div style=\"text-align:center\">$text</div></html>" else ""
  }
}
