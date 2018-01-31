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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.model.stdui.CommonTextFieldModel
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.GridLayout
import javax.swing.*

/**
 * Tester for misc common controls.
 */
object CommonControlPortfolio {
  private val ourFont = UIUtil.getFontWithFallback("Ariel", 0, 12)

  @JvmStatic fun main(args: Array<String>) {
    SwingUtilities.invokeLater { createAndShowGUI() }
  }

  private fun createAndShowGUI() {
    setLAF(UIManager.getLookAndFeel())

    //Create and set up the window.
    val frame = JFrame("ASTextField Demo")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

    //Set up the content pane.
    addComponentsToPane(frame.contentPane)
    updateFonts(frame)

    //Display the window.
    frame.pack()
    frame.setSize(250, frame.height)
    frame.isVisible = true
  }

  private fun addComponentsToPane(contentPane: Container) {
    val grid = JPanel()
    grid.layout = GridLayout(8, 1, 5, 5)
    grid.border = JBUI.Borders.empty(20, 20, 20, 20)
    grid.isOpaque = false

    grid.add(makeTextField("Normal", true))
    grid.add(makeTextField("Disabled", false))
    grid.add(makeTextField("Error", true))
    grid.add(makeTextField("", true))
    grid.add(makeComboBox("zero", true, true))
    grid.add(makeComboBox("zero", true, false))
    grid.add(makeLAFControl())

    val topPanel = JPanel(BorderLayout())
    topPanel.isOpaque = false
    topPanel.add(grid, BorderLayout.NORTH)
    topPanel.add(JPanel(), BorderLayout.CENTER)

    contentPane.layout = BorderLayout()
    contentPane.add(topPanel, BorderLayout.CENTER)
    contentPane.background = secondaryPanelBackground
  }

  private fun makeLAFControl(): JComponent {
    val control = JCheckBox("Darcula")
    control.putClientProperty("LAF", UIManager.getLookAndFeel())
    control.addItemListener { _ ->
      val laf = if (control.isSelected) DarculaLaf() else control.getClientProperty("LAF") as LookAndFeel
      if (laf !== UIManager.getLookAndFeel()) {
        setLAF(laf)
        updateFonts(SwingUtilities.getWindowAncestor(control))
        SwingUtilities.updateComponentTreeUI(SwingUtilities.getWindowAncestor(control))
      }
    }
    return control
  }

  private fun setLAF(laf: LookAndFeel) {
    try {
      UIManager.setLookAndFeel(laf)
      JBColor.setDark(laf is DarculaLaf)
    }
    catch (ex: Exception) {
      ex.printStackTrace()
    }

  }

  private fun updateFonts(component: Component) {
    component.font = ourFont
    if (component is Container) {
      for (child in component.components) {
        updateFonts(child)
      }
    }
  }

  private fun makeTextField(text: String, enabled: Boolean): JComponent {
    val model = object : CommonTextFieldModel {
      override val value = text

      override val enabled: Boolean
        get() = enabled

      override val placeHolderValue: String
        get() = "@+id/name"

      override fun validationError(editedValue: String): String {
        return if (editedValue == "Error") "Invalid text content" else ""
      }

      override fun addListener(listener: ValueChangedListener) {

      }

      override fun removeListener(listener: ValueChangedListener) {

      }
    }
    val field = CommonTextField(model)
    field.isOpaque = false
    if (text == "Disabled") {
      field.isEnabled = false
    }
    return field
  }

  private fun makeComboBox(initialValue: String, enabled: Boolean, editable: Boolean): JComponent {
    val model = DefaultCommonComboBoxModel(initialValue, listOf("one", "two", "three", "four", "five", "six"))
    model.enabled = enabled
    model.editable = editable
    model.placeHolderValue = "@+id/name"

    val combo = CommonComboBox(model)
    val inset = combo.insets.left
    combo.isOpaque = false
    combo.renderer = SimpleListRenderer(0, inset)
    return combo
  }
}

class SimpleListRenderer(private val valueInset: Int, private val listInset: Int) :
    ColoredListCellRenderer<String>() {

  override fun customizeCellRenderer(list: JList<out String>, value: String?, index: Int, selected: Boolean, hasFocus: Boolean) {
    ipad.left = if (index < 0) valueInset else listInset
    ipad.right = 0
    if (value != null) {
      append(value)
    }
  }
}
