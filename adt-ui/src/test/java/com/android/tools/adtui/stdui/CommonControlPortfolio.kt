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
import com.android.tools.adtui.model.stdui.CommonAction
import com.android.tools.adtui.model.stdui.CommonTextFieldModel
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.menu.CommonDropDownButton
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.*
import javax.swing.*

/**
 * Tester for misc common controls.
 */
object CommonControlPortfolio {
  private val ourFont = UIUtil.getFontWithFallback("Ariel", 0, 12)

  @JvmStatic
  fun main(args: Array<String>) {
    IconLoader.activate()
    SwingUtilities.invokeLater { createAndShowGUI() }
  }

  private fun createAndShowGUI() {
    setLAF(UIManager.getLookAndFeel())

    //Create and set up the window.
    val frame = JFrame("Common Controls")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

    //Set up the content pane.
    addComponentsToPane(frame.contentPane)
    updateFonts(frame)

    //Display the window.
    frame.pack()
    frame.setSize(500, frame.height)
    frame.isVisible = true
  }

  private fun addComponentsToPane(contentPane: Container) {
    var grid = JPanel(VerticalFlowLayout())
    grid.add(makeTextField("Normal", true))
    grid.add(makeTextField("Disabled", false))
    grid.add(makeTextField("Error", true))
    grid.add(makeTextField("", true))

    val topPanel = CommonTabbedPane()
    topPanel.add(grid, "TextField")

    grid = JPanel(VerticalFlowLayout())
    grid.add(makeComboBox("zero", true, true))
    grid.add(makeComboBox("zero", true, false))
    topPanel.add(grid, "ComboBox")

    var menuPanel = JPanel(VerticalFlowLayout())
    var label = JLabel()
    var toolBarPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    toolBarPanel.add(makeDropDownMenu("MenuWithIconArrow", StudioIcons.Common.ADD, true, 2, 2, label))
    toolBarPanel.add(makeDropDownMenu("MenuNoIcon", null, false, 2, 2, label))
    toolBarPanel.add(makeDropDownMenu("", StudioIcons.Common.EXPORT, true, 2, 2, label))
    toolBarPanel.add(makeDropDownMenu("", StudioIcons.Common.FILTER, false, 2, 2, label))
    menuPanel.add(toolBarPanel, BorderLayout.PAGE_START)
    menuPanel.add(label, BorderLayout.CENTER)
    topPanel.add(menuPanel, "Menus")

    grid = JPanel()
    grid.layout = GridLayout(2, 2, 5, 5)
    grid.border = JBUI.Borders.empty(20, 20, 20, 20)

    listOf(SwingConstants.TOP, SwingConstants.RIGHT, SwingConstants.LEFT, SwingConstants.BOTTOM).forEach {
      val tab = CommonTabbedPane()
      tab.border = BorderFactory.createLineBorder(StandardColors.TAB_BORDER_COLOR, 1)
      tab.tabPlacement = it
      listOf("One", "Two", "Three").forEach { tab.add(JLabel("Label $it"), it) }
      grid.add(tab)
    }
    topPanel.add(grid, "Tabs")

    contentPane.layout = BorderLayout()
    contentPane.add(topPanel, BorderLayout.CENTER)
    contentPane.add(makeLAFControl(), BorderLayout.SOUTH)
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
    } catch (ex: Exception) {
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

  private fun makeDropDownMenu(text: String, icon: Icon?, showArrow: Boolean, width: Int, depth: Int, label: JLabel): JComponent {
    var model = CommonAction(text, icon, null)
    model.showExpandArrow = showArrow

    for (i in 0 until width) {
      if (i % 2 == 0) {
        var action = CommonAction(text, icon)
        action.setAction(
            {
              action.isSelected = !action.isSelected
              label.text = String.format("Clicked: %s", action)
            }
        )
        model.addChildrenActions(action)
      } else {
        var action = CommonAction(text, icon)
        populateCommonActionRecursive(action, text, icon, width, depth - 1, label)
        model.addChildrenActions(action)
      }
    }

    return CommonDropDownButton(model)
  }

  private fun populateCommonActionRecursive(parent: CommonAction, text: String, icon: Icon?, width: Int, depth: Int, label: JLabel) {
    for (i in 0 until width) {
      if (i % 2 == 0 || depth - 1 == 0) {
        var action = CommonAction(text, icon)
        action.setAction(
            {
              action.isSelected = !action.isSelected
              label.text = String.format("Clicked: %s", action)
            }
        )
        parent.addChildrenActions(action)
      } else {
        var action = CommonAction(text, icon)
        populateCommonActionRecursive(action, text, icon, width, depth - 1, label)
        parent.addChildrenActions(action)
      }
    }
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
