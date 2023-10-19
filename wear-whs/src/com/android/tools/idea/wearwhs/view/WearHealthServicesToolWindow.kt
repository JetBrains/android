/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

private const val PADDING = 15

private val HORIZONTAL_BORDERS = JBUI.Borders.empty(0, PADDING)

internal class WearHealthServicesToolWindow : SimpleToolWindowPanel(true, true) {

  private val contentPanel = run {
    val header = JPanel(BorderLayout()).apply {
      border = HORIZONTAL_BORDERS
      add(ComboBox(arrayOf(message("wear.whs.panel.capabilities.standard"), message("wear.whs.panel.capabilities.all"))),
          BorderLayout.WEST)
      add(JLabel(message("wear.whs.panel.test.data.inactive")), BorderLayout.EAST)
    }
    val content = JBScrollPane().apply {
      setViewportView(JPanel(VerticalFlowLayout()).apply {
        border = HORIZONTAL_BORDERS
        add(JPanel(BorderLayout()).apply {
          add(JLabel(message("wear.whs.panel.sensor")).apply {
            font = font.deriveFont(Font.BOLD)
          }, BorderLayout.CENTER)
          add(JPanel(FlowLayout()).apply {
            add(JLabel(message("wear.whs.panel.override")).apply {
              font = font.deriveFont(Font.BOLD)
            })
            add(JLabel(message("wear.whs.panel.unit")).apply {
              font = font.deriveFont(Font.BOLD)
              preferredSize = Dimension(50, preferredSize.height)
            })
          }, BorderLayout.EAST)
        })
        WHS_CAPABILITIES.forEach {
          add(JPanel(BorderLayout()).apply {
            preferredSize = Dimension(0, 35)
            val checkBox = JCheckBox(message(it.labelKey))
            add(checkBox, BorderLayout.CENTER)
            add(JPanel(FlowLayout()).apply {
              add(JTextField().apply {
                preferredSize = Dimension(50, preferredSize.height)
                isEnabled = checkBox.isSelected
                checkBox.selected.addListener {
                  isEnabled = it
                }
                isVisible = it.isOverrideable
              })
              add(JLabel(message(it.unitKey)).apply {
                isVisible = it.isOverrideable
                preferredSize = Dimension(50, preferredSize.height)
              })
            }, BorderLayout.EAST)
          })
        }
      })
    }
    val footer = JPanel(FlowLayout(FlowLayout.TRAILING)).apply {
      border = HORIZONTAL_BORDERS
      add(JButton(message("wear.whs.panel.reset")))
      add(JButton(message("wear.whs.panel.apply")))
    }
    JPanel(BorderLayout()).apply {
      add(header, BorderLayout.NORTH)
      add(content, BorderLayout.CENTER)
      add(footer, BorderLayout.SOUTH)
    }
  }

  init {
    add(contentPanel)
  }
}
