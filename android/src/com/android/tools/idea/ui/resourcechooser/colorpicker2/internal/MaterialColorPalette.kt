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
package com.android.tools.idea.ui.resourcechooser.colorpicker2.internal

import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.ui.MaterialColors
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerModel
import com.android.tools.idea.ui.resourcechooser.colorpicker2.PICKER_BACKGROUND_COLOR
import com.android.tools.idea.ui.resourcechooser.colorpicker2.PICKER_PREFERRED_WIDTH
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

private const val COLOR_BUTTON_NUMBER = 16

private val PALETTE_PREFERRED_SIZE = JBUI.size(PICKER_PREFERRED_WIDTH, 125)
private val PALETTE_BORDER = JBUI.Borders.empty()

private val COMBO_BOX_INSETS = JBUI.insets(0, 4, 4, 4)

private val COLOR_BUTTON_SIZE = JBUI.size(34)
private val COLOR_BUTTON_BORDER = JBUI.Borders.empty(6)
private val COLOR_BUTTON_INSETS = JBUI.insets(0)

class MaterialColorPalette(private val pickerModel: ColorPickerModel) : JPanel() {

  @get:TestOnly
  val colorButtons = Array(COLOR_BUTTON_NUMBER, {
    ColorButton().apply {
      background = PICKER_BACKGROUND_COLOR
      addActionListener { pickerModel.setColor(color, ColorPalette@this) }
    }
  })

  init {
    layout = GridBagLayout()
    border = PALETTE_BORDER
    preferredSize = PALETTE_PREFERRED_SIZE
    background = PICKER_BACKGROUND_COLOR

    // TODO: avoid using GridBagConstraints
    val c = GridBagConstraints()
    c.fill = GridBagConstraints.HORIZONTAL
    c.gridwidth = 8
    c.gridx = 0
    c.gridy = 0
    c.insets = COMBO_BOX_INSETS
    val boxModel = MyComboBoxModel(MaterialColors.Category.values())
    val box = CommonComboBox(boxModel)
    box.addActionListener { setColorSet(boxModel.selectedItem as MaterialColors.Category) }
    add(box, c)

    c.gridwidth = 1
    c.gridx = 1
    c.insets = COLOR_BUTTON_INSETS

    // Add buttons for built-in color
    val halfButtonNumber = COLOR_BUTTON_NUMBER / 2
    for (colorIndex in 0 until COLOR_BUTTON_NUMBER) {
      c.gridx = colorIndex % halfButtonNumber
      c.gridy = 1 + colorIndex / halfButtonNumber
      add(colorButtons[colorIndex], c)
    }

    val category = loadLastUsedColorCategory()
    box.selectedItem = category
    setColorSet(category)
  }

  private fun setColorSet(category: MaterialColors.Category) {
    val colorSet = MaterialColors.getColorSet(category)
    saveLastUsedColorCategory(category)
    MaterialColors.Color.values()
      .filter { it.ordinal < colorButtons.size }
      .forEach { colorButtons[it.ordinal].color = colorSet[it]!! }
    repaint()
  }
}

private class MyComboBoxModel(colorCategories: Array<MaterialColors.Category>)
  : DefaultComboBoxModel<MaterialColors.Category>(), CommonComboBoxModel<MaterialColors.Category> {

  init {
    colorCategories.forEach { addElement(it) }
  }

  override var value = (selectedItem as MaterialColors.Category).displayName

  override var text = (selectedItem as MaterialColors.Category).displayName

  override var editable = false
    private set

  override fun addListener(listener: ValueChangedListener) = Unit

  override fun removeListener(listener: ValueChangedListener) = Unit
}

class ColorButton(var color: Color = Color.WHITE): JButton() {

  enum class Status { NORMAL, HOVER, PRESSED }

  var status = Status.NORMAL

  init {
    preferredSize = COLOR_BUTTON_SIZE
    border = COLOR_BUTTON_BORDER
    isRolloverEnabled = true
    hideActionText = true
    background = PICKER_BACKGROUND_COLOR

    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        status = Status.HOVER
        repaint()
      }

      override fun mouseExited(e: MouseEvent) {
        status = Status.NORMAL
        repaint()
      }

      override fun mousePressed(e: MouseEvent) {
        status = Status.PRESSED
        repaint()
      }

      override fun mouseReleased(e: MouseEvent) {
        status = when (status) {
          Status.PRESSED -> Status.HOVER
          else -> Status.NORMAL
        }
        repaint()
      }
    })
  }

  override fun paintComponent(g: Graphics) {
    val g2d = g as Graphics2D
    val originalAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    val originalColor = g.color

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    // Cleanup background
    g.color = background
    g.fillRect(0, 0, width, height)

    if (status == Status.HOVER || status == Status.PRESSED) {
      val l = insets.left / 2
      val t = insets.top / 2
      val w = width - l - insets.right / 2
      val h = height - t - insets.bottom / 2

      val focusColor = UIUtil.getFocusedBoundsColor() ?: Color.LIGHT_GRAY
      g.color = if (status == Status.HOVER) focusColor else focusColor.darker()
      g2d.fillRoundRect(l, t, w, h, 7, 7)
    }
    g.color = color
    g2d.fillRoundRect(insets.left, insets.top, width - insets.left - insets.right, height - insets.top - insets.bottom, 5, 5)
    g.color = originalColor

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing)
  }
}

private const val COLOR_PICKER_CATEGORY_PROPERTY = "colorPickerCategoryProperty"
private val DEFAULT_COLOR_CATEGORY = MaterialColors.Category.MATERIAL_500

private fun loadLastUsedColorCategory(): MaterialColors.Category {
  val modeName = PropertiesComponent.getInstance().getValue(
    COLOR_PICKER_CATEGORY_PROPERTY, DEFAULT_COLOR_CATEGORY.name)
  return try {
    MaterialColors.Category.valueOf(modeName)
  }
  catch (e: IllegalArgumentException) {
    // If the code reach here, that means some of unexpected category is saved as user's preference.
    // In this case, return the default category instead.
    Logger.getInstance(MaterialColorPalette::class.java)
      .warn("The color category $modeName is not recognized, use default category $DEFAULT_COLOR_CATEGORY instead")
    DEFAULT_COLOR_CATEGORY
  }
}

private fun saveLastUsedColorCategory(category: MaterialColors.Category)
  = PropertiesComponent.getInstance().setValue(
  COLOR_PICKER_CATEGORY_PROPERTY, category.name)
