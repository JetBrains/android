/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipComponent
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseAdapter
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class Tooltip(owner: JComponent, pane: TooltipLayeredPane) {

  val adapter: MouseAdapter

  val isVisible: Boolean
    get() = tooltipComponent.isVisible

  var tooltipInfo: TooltipInfo? = null
    set(value) {
      field = value
      value?.let {
        tooltipHeader.text = it.header
        tooltipDescription.text = it.description
      }
    }

  private val tooltipHeader = JLabel("").apply { font = JBFont.regular() }
  private val tooltipDescription =
    JLabel("").apply {
      font = JBFont.regular()
      foreground = UIUtil.getContextHelpForeground()
    }

  private val tooltipComponent: TooltipComponent

  init {
    val textPane =
      JPanel(TabularLayout("Fit-")).also {
        it.border = JBEmptyBorder(8, 10, 8, 10)
        it.background = InspectorColors.TOOLTIP_BACKGROUND_COLOR
        it.foreground = InspectorColors.TOOLTIP_TEXT_COLOR
        it.add(tooltipHeader, TabularLayout.Constraint(0, 0))
        it.add(tooltipDescription, TabularLayout.Constraint(1, 0))
      }
    tooltipComponent =
      TooltipComponent.Builder(textPane, owner, pane).build().apply {
        this.registerListenersOn(textPane)
        adapter = textPane.mouseListeners.first() as MouseAdapter
        textPane.removeMouseListener(adapter)
        textPane.mouseMotionListeners.first().let { textPane.removeMouseMotionListener(it) }
      }
  }
}
