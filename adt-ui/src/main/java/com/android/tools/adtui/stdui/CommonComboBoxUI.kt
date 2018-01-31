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

import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.stdui.StandardDimensions.DROPDOWN_ARROW_HEIGHT
import com.android.tools.adtui.stdui.StandardDimensions.DROPDOWN_ARROW_WIDTH
import com.android.tools.adtui.stdui.StandardDimensions.DROPDOWN_BUTTON_WIDTH
import com.android.tools.adtui.stdui.StandardDimensions.DROPDOWN_CORNER_RADIUS
import com.android.tools.adtui.stdui.StandardDimensions.INNER_BORDER_WIDTH
import com.android.tools.adtui.stdui.StandardDimensions.OUTER_BORDER_WIDTH
import com.intellij.util.ui.JBUI
import sun.swing.UIAction
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.beans.PropertyChangeEvent
import java.lang.Math.round
import javax.swing.*
import javax.swing.plaf.ActionMapUIResource
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.InputMapUIResource
import javax.swing.plaf.UIResource
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.plaf.basic.BasicComboBoxUI
import javax.swing.plaf.basic.BasicComboPopup
import javax.swing.plaf.basic.ComboPopup

private const val GLOBAL_INPUT_MAP = "ASComboBox.inputMap"
private const val GLOBAL_ACTION_MAP = "ASComboBox.actionMap"

open class CommonComboBoxUI : BasicComboBoxUI() {

  override fun installDefaults() {
    super.installDefaults()

    val border = comboBox.border
    if (border == null || border is UIResource) {
      comboBox.border = StandardBorder(DROPDOWN_CORNER_RADIUS)
    }
    val background = comboBox.background
    if (background == null || background is UIResource) {
      comboBox.background = ColorUIResource(StandardColors.BACKGROUND_COLOR)
    }
  }

  override fun getMinimumSize(component: JComponent): Dimension {
    if (!isMinimumSizeDirty) {
      return Dimension(cachedMinimumSize)
    }
    val size = displaySize
    val insets = insets
    val buttonWidth = round(DROPDOWN_BUTTON_WIDTH)
    //adjust the size based on the button width
    size.height += insets.top + insets.bottom
    size.width += insets.left + insets.right + buttonWidth

    cachedMinimumSize.setSize(size.width, size.height)
    isMinimumSizeDirty = false

    return Dimension(size)
  }

  override fun createPopup(): ComboPopup {
    return object : BasicComboPopup(comboBox) {
      override fun configurePopup() {
        super.configurePopup()
        border = JBUI.Borders.customLine(StandardColors.INNER_BORDER_COLOR)
      }

      override fun computePopupBounds(x: Int, y: Int, width: Int, height: Int): Rectangle  {
        val border = round(OUTER_BORDER_WIDTH)
        return super.computePopupBounds(x + border, y, width - 2 * border, height)
      }

      override fun show() {
        // Before we show the popup select the current comboBox value if it exist in the list
        comboBox.selectedItem = comboBox.editor.item

        // And hide the editor caret
        val editor = comboBox.editor?.editorComponent as? JTextField
        editor?.caret?.isVisible = false

        super.show()
      }

      override fun hide() {
        // Restore the editor caret
        val editor = comboBox.editor?.editorComponent as? JTextField
        if (editor?.hasFocus() == true) {
          editor.caret.isVisible = true
        }

        super.hide()
      }
    }
  }

  override fun paint(g: Graphics, c: JComponent) {
    val g2 = g.create()
    val model = comboBox.model as? CommonComboBoxModel
    val textEditor = editor as? JTextField
    val hasErrors = model?.validationError(textEditor?.text ?: "")?.isNotEmpty() == true
    val hasFocus = if (model?.editable == true) editor?.isFocusOwner == true else comboBox.isFocusOwner
    val hasVisiblePlaceHolder = textEditor?.text?.isEmpty() == true && model?.placeHolderValue?.isNotEmpty() == true
    (comboBox.border as? StandardBorder)?.paintBorder(comboBox, g2, hasErrors, hasFocus, hasVisiblePlaceHolder)
    paintArrowButton(g)
    g2.dispose()
    super.paint(g, c)
  }

  override fun paintCurrentValueBackground(g: Graphics, bounds: Rectangle, hasFocus: Boolean) {
    val prevColor = g.color
    g.color = StandardColors.BACKGROUND_COLOR
    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
    g.color = prevColor
  }

  private fun paintArrowButton(g: Graphics) {
    val rect = createRectWithBorderInsets()
    val g2 = g as Graphics2D
    val line = Path2D.Float()
    line.moveTo(rect.x + rect.width - DROPDOWN_BUTTON_WIDTH - 0.5f, rect.y)
    line.lineTo(rect.x + rect.width - DROPDOWN_BUTTON_WIDTH - 0.5f, rect.y + rect.height - 0.5f)
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.stroke = BasicStroke(INNER_BORDER_WIDTH)
    g2.setColorAndAlpha(StandardColors.INNER_BORDER_COLOR)
    g2.draw(line)

    val y = rect.y + (rect.height - DROPDOWN_ARROW_HEIGHT) / 2f + 1f
    val x = rect.x + rect.width - DROPDOWN_BUTTON_WIDTH / 2f
    val triangle = Path2D.Float()
    triangle.moveTo(x, y + DROPDOWN_ARROW_HEIGHT)
    triangle.lineTo(x - DROPDOWN_ARROW_WIDTH / 2f, y)
    triangle.lineTo(x + DROPDOWN_ARROW_WIDTH / 2f, y)
    triangle.lineTo(x, y + DROPDOWN_ARROW_HEIGHT)
    g2.setColorAndAlpha(StandardColors.DROPDOWN_ARROW_COLOR)
    g2.fill(triangle)
  }

  private fun createRectWithBorderInsets(): Rectangle2D.Float {
    val rect = Rectangle2D.Float(0f, 0f, comboBox.width.toFloat(), comboBox.height.toFloat())
    if (comboBox.border is StandardBorder) {
      rect.applyInset(OUTER_BORDER_WIDTH)
      rect.applyInset(INNER_BORDER_WIDTH)
    }
    else {
      val insets = comboBox.border?.getBorderInsets(comboBox) ?: JBUI.insets(0)
      rect.x += insets.left.toFloat()
      rect.y += insets.top.toFloat()
      rect.width += insets.left.toFloat() + insets.right.toFloat()
      rect.height += insets.left.toFloat() + insets.bottom.toFloat()
    }
    return rect
  }

  override fun createArrowButton() : JButton? {
    return null
  }

  override fun createEditor(): ComboBoxEditor {
    val model = comboBox.model as? CommonComboBoxModel ?: return super.createEditor()
    return object : BasicComboBoxEditor.UIResource() {
      override fun createEditorComponent(): JTextField {
        val editor = object : CommonTextField(model) {
          override fun processKeyEvent(event: KeyEvent) {
            if (comboBox.isPopupVisible || willOpenPopup(event)) {
              comboBox.processKeyEvent(event)
            }
            else {
              super.processKeyEvent(event)
            }
          }

          private fun willOpenPopup(event: KeyEvent): Boolean {
            val code = event.keyCode
            return (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) && event.modifiers == 0
          }
        }
        editor.border = JBUI.Borders.empty()
        editor.addFocusListener(object : FocusAdapter() {
          override fun focusGained(event: FocusEvent) {
            comboBox.revalidate()
            comboBox.repaint()
          }

          override fun focusLost(event: FocusEvent) {
            comboBox.repaint()
          }
        })
        comboBox.addPropertyChangeListener(object : PropertyChangeHandler() {
          override fun propertyChange(event: PropertyChangeEvent) {
            if (event.propertyName == "editable") {
              editor.isEditable = comboBox.isEditable
            }
          }
        })
        return editor
      }
    }
  }

  override fun installKeyboardActions() {
    SwingUtilities.replaceUIInputMap(comboBox, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, getInputMap())
    SwingUtilities.replaceUIActionMap(comboBox, getActionMap())
  }

  override fun uninstallKeyboardActions() {
    SwingUtilities.replaceUIInputMap(comboBox, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null)
    SwingUtilities.replaceUIActionMap(comboBox, null)
  }

  private fun getInputMap(): InputMap {
    return UIManager.get(GLOBAL_INPUT_MAP) as InputMap? ?: createInputMap()
  }

  private fun createInputMap(): InputMap {
    val map = InputMapUIResource()
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ActionType.HIDE_POPUP)
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ActionType.ENTER)
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), ActionType.SELECT_PAGE_UP)
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), ActionType.SELECT_PAGE_DOWN)
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, KeyEvent.CTRL_DOWN_MASK), ActionType.SELECT_TOP)
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, KeyEvent.CTRL_DOWN_MASK), ActionType.SELECT_BOTTOM)
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), ActionType.SELECT_PREV)
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ActionType.SELECT_NEXT)
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK), ActionType.HIDE_POPUP)
    map.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK), ActionType.TOGGLE_POPUP)
    UIManager.getLookAndFeelDefaults().put(GLOBAL_INPUT_MAP, map)
    return map
  }

  private fun getActionMap(): ActionMap {
    return UIManager.get(GLOBAL_ACTION_MAP) as ActionMap? ?: createActionMap()
  }

  private fun createActionMap(): ActionMap {
    val map = ActionMapUIResource()
    for (type in ActionType.values()) {
      map.put(type, ComboAction(type))
    }
    UIManager.getLookAndFeelDefaults().put(GLOBAL_ACTION_MAP, map)
    return map
  }

  private enum class ActionType {
    HIDE_POPUP,
    TOGGLE_POPUP,
    ENTER,
    SELECT_PREV,
    SELECT_NEXT,
    SELECT_PAGE_UP,
    SELECT_PAGE_DOWN,
    SELECT_TOP,
    SELECT_BOTTOM
  }

  private class ComboAction(private val type: ActionType) : UIAction(type.name) {

    override fun actionPerformed(event: ActionEvent) {
      val comboBox = event.source as? JComboBox<*> ?: return
      when (type) {
        ActionType.HIDE_POPUP ->
          comboBox.setPopupVisible(false)
        ActionType.TOGGLE_POPUP ->
          comboBox.setPopupVisible(!comboBox.isPopupVisible)
        ActionType.ENTER ->
          enter(comboBox)
        else ->
          moveTo(comboBox, type)
      }
    }

    private fun enter(comboBox: JComboBox<*>) {
      val ui = comboBox.ui as? CommonComboBoxUI ?: return
      val listItem = ui.popup.list.selectedValue
      if (listItem != null) {
        comboBox.editor.item = listItem
        comboBox.selectedItem = listItem
      }
      comboBox.isPopupVisible = false
    }

    private fun moveTo(comboBox: JComboBox<*>, type: ActionType) {
      if (!comboBox.isPopupVisible) {
        comboBox.setPopupVisible(true)
      }
      else {
        comboBox.selectedIndex = getNextIndex(comboBox, type).coerceIn(0, comboBox.itemCount - 1)
      }
    }

    private fun getNextIndex(comboBox: JComboBox<*>, type: ActionType) =
      when (type) {
        ActionType.SELECT_PREV -> comboBox.selectedIndex - 1
        ActionType.SELECT_NEXT -> comboBox.selectedIndex + 1
        ActionType.SELECT_PAGE_UP -> comboBox.selectedIndex - comboBox.maximumRowCount
        ActionType.SELECT_PAGE_DOWN -> comboBox.selectedIndex + comboBox.maximumRowCount
        ActionType.SELECT_TOP -> 0
        ActionType.SELECT_BOTTOM -> comboBox.itemCount - 1
        else -> comboBox.selectedIndex
      }
  }
}
