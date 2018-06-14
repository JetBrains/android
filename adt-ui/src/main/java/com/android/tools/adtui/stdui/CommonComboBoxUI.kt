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

import com.android.tools.adtui.model.stdui.CommonBorderModel
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.CommonTextFieldModel
import com.android.tools.adtui.stdui.StandardDimensions.DROPDOWN_ARROW_HEIGHT
import com.android.tools.adtui.stdui.StandardDimensions.DROPDOWN_ARROW_WIDTH
import com.android.tools.adtui.stdui.StandardDimensions.DROPDOWN_CORNER_RADIUS
import com.android.tools.adtui.stdui.StandardDimensions.HORIZONTAL_PADDING
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
import javax.swing.plaf.*
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.plaf.basic.BasicComboBoxUI
import javax.swing.plaf.basic.BasicComboPopup
import javax.swing.plaf.basic.ComboPopup
import kotlin.math.max
import kotlin.math.roundToInt

private const val GLOBAL_INPUT_MAP = "ASComboBox.inputMap"
private const val GLOBAL_ACTION_MAP = "ASComboBox.actionMap"

open class CommonComboBoxUI : BasicComboBoxUI() {

  override fun installDefaults() {
    super.installDefaults()

    if (comboBox.border == null || comboBox.border is UIResource) {
      comboBox.border = BorderUIResource(CommonBorder(DROPDOWN_CORNER_RADIUS, EditorCommonBorderModel(comboBox), JBUI.emptyInsets()))
    }
    if (comboBox.background == null || comboBox.background is UIResource) {
      comboBox.background = ColorUIResource(StandardColors.BACKGROUND_COLOR)
    }
    LookAndFeel.installProperty(comboBox, "opaque", false)
  }

  // Fudge the display size such that TextFields and comboBoxes are about the same height
  override fun getDisplaySize(): Dimension {
    val metrics = comboBox.getFontMetrics(comboBox.font)
    val h = max(metrics.height + JBUI.scale(1), (3 * DROPDOWN_ARROW_HEIGHT).roundToInt())
    val w = (3 * DROPDOWN_ARROW_WIDTH).roundToInt() + 10 * metrics.charWidth('w')
    return Dimension(w, h)
  }

  override fun getMinimumSize(component: JComponent): Dimension {
    if (!isMinimumSizeDirty) {
      return Dimension(cachedMinimumSize)
    }
    val size = displaySize
    val insets = insets
    size.height += insets.top + insets.bottom
    size.width += insets.left + insets.right + (2 * DROPDOWN_ARROW_WIDTH).roundToInt()

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

      override fun configureList() {
        super.configureList()
        list.componentOrientation = comboBox.componentOrientation
      }

      override fun computePopupBounds(x: Int, y: Int, width: Int, height: Int): Rectangle  {
        val border = round(OUTER_BORDER_WIDTH)
        return super.computePopupBounds(x + border, y, width - 2 * border, height)
      }
    }
  }

  override fun paint(g: Graphics, c: JComponent) {
    paintArrowButton(g)
    paintMargin(g)
    super.paint(g, c)
  }

  override fun paintCurrentValueBackground(g: Graphics, bounds: Rectangle, hasFocus: Boolean) {
    val prevColor = g.color
    g.color = comboBox.background
    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
    g.color = prevColor
  }

  override fun paintCurrentValue(g: Graphics, bounds: Rectangle, hasFocus: Boolean) {
    val component = comboBox.renderer.getListCellRendererComponent(listBox, comboBox.selectedItem, -1, false, hasFocus)
    component.font = comboBox.font
    component.background = comboBox.background

    // Fix for 4238829: should lay out the JPanel.
    val shouldValidate = component is JPanel

    currentValuePane.paintComponent(g, component, comboBox, bounds.x, bounds.y, bounds.width, bounds.height, shouldValidate)
  }

  private fun paintArrowButton(g: Graphics) {
    val g2 = g.create() as Graphics2D
    val rect = rectangleForArrowButton()
    val corner = DROPDOWN_CORNER_RADIUS

    // Fill background of arrow button
    val background = Path2D.Float()
    adjustForCurvedBorder(rect, corner - 1f)
    background.moveTo(rect.x, rect.y)
    background.lineTo(rect.x + rect.width, rect.y)
    background.lineTo(rect.x + rect.width, rect.y + rect.height)
    background.lineTo(rect.x, rect.y + rect.height)
    background.closePath()
    adjustForCurvedBorder(rect, -(corner - 1f))
    g2.color = comboBox.background
    g2.fill(background)

    // Draw a line to separate the arrow button from the value/editor
    val xLine = if (comboBox.componentOrientation.isLeftToRight) rect.x else rect.x + rect.width
    val line = Path2D.Float()
    line.moveTo(xLine, rect.y)
    line.lineTo(xLine, rect.y + rect.height - 0.5f)
    g2.stroke = BasicStroke(INNER_BORDER_WIDTH)
    g2.color = StandardColors.INNER_BORDER_COLOR
    g2.draw(line)

    // Draw the arrow of the button
    val w2 = rect.width / 4f
    val h = rect.height / 4f
    val x = rect.x + rect.width / 2f
    val y = rect.y + (rect.height - h) / 2f
    val triangle = Path2D.Float()
    triangle.moveTo(x, y + h)
    triangle.lineTo(x - w2, y)
    triangle.lineTo(x + w2, y)
    triangle.closePath()
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.color = StandardColors.DROPDOWN_ARROW_COLOR
    g2.fill(triangle)
    g2.dispose()
  }

  private fun paintMargin(g: Graphics) {
    val g2 = g as Graphics2D
    val background = Path2D.Float()
    val rect = rectangleForMargin()
    background.moveTo(rect.x, rect.y)
    background.lineTo(rect.x + rect.width, rect.y)
    background.lineTo(rect.x + rect.width, rect.y + rect.height)
    background.lineTo(rect.x, rect.y + rect.height)
    background.closePath()
    g2.color = comboBox.background
    g2.fill(background)
  }

  private fun rectangleForArrowButton(): Rectangle2D.Float {
    val bounds = Rectangle2D.Float(0f, 0f, comboBox.width.toFloat(), comboBox.height.toFloat())
    bounds.applyInsets(insets)

    // The width of the button depends on the height. See BasicComboBoxUI.rectangleForCurrentValue.
    val buttonWidth = bounds.height

    // The placement of the arrow button depends on the component orientation.
    if (comboBox.componentOrientation.isLeftToRight) {
      bounds.x = max(0f, bounds.x + bounds.width - buttonWidth)
    }
    bounds.width = buttonWidth
    return bounds
  }

  private fun rectangleForMargin(): Rectangle2D.Float {
    val bounds = Rectangle2D.Float(0f, 0f, comboBox.width.toFloat(), comboBox.height.toFloat())
    bounds.applyInsets(insets)
    if (comboBox.componentOrientation.isLeftToRight) {
      bounds.x += DROPDOWN_CORNER_RADIUS - 1
    } else {
      bounds.x = bounds.x + bounds.width - HORIZONTAL_PADDING
    }
    bounds.width = HORIZONTAL_PADDING - DROPDOWN_CORNER_RADIUS + 1
    return bounds
  }

  override fun rectangleForCurrentValue(): Rectangle {
    val rect = super.rectangleForCurrentValue()
    rect.width -= HORIZONTAL_PADDING
    if (comboBox.componentOrientation.isLeftToRight) {
      rect.x += HORIZONTAL_PADDING
    }
    return rect
  }

  private fun adjustForCurvedBorder(bounds: Rectangle2D.Float, size: Float) {
    bounds.width -= size
    if (!comboBox.componentOrientation.isLeftToRight) {
      bounds.x += size
    }
  }

  override fun createArrowButton() : JButton? {
    return null
  }

  // Create a default renderer.
  // Note: This method will only be called if no renderer was specified on the ComboBox.
  override fun createRenderer(): ListCellRenderer<*> {
    return CommonComboBoxRenderer.UIResource()
  }

  override fun createEditor(): ComboBoxEditor {
    val model = comboBox.model as? CommonComboBoxModel ?: return super.createEditor()
    return object : BasicComboBoxEditor.UIResource() {
      override fun createEditorComponent(): JTextField {
        val editor = object : CommonTextField<CommonTextFieldModel>(model) {
          override fun processKeyEvent(event: KeyEvent) {
            if (willOpenPopup(event)) {
              comboBox.processKeyEvent(event)
            }
            else {
              super.processKeyEvent(event)
            }
          }

          override fun repaint() {
            comboBox.repaint()
          }

          private fun willOpenPopup(event: KeyEvent): Boolean {
            val code = event.keyCode
            return (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) && event.modifiers == 0
          }
        }
        editor.border = JBUI.Borders.empty()
        editor.background = comboBox.background
        editor.componentOrientation = comboBox.componentOrientation
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
    UIManager.getLookAndFeelDefaults()[GLOBAL_INPUT_MAP] = map
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
    UIManager.getLookAndFeelDefaults()[GLOBAL_ACTION_MAP] = map
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

  // Note: this violates the golden rule about not having component references in a model.
  // However in this case initDefaults() are being called in the constructor of JTextField
  // before the constructor of CommonTextField has been executed.
  private class EditorCommonBorderModel(private val comboBox: JComboBox<*>): CommonBorderModel {
    override val hasError: Boolean
      get() = model?.validate(text)?.isNotEmpty() == true

    override val hasPlaceHolder: Boolean
      get() = text.isEmpty() && model?.placeHolderValue?.isNotEmpty() == true

    private val model: CommonComboBoxModel<*>?
      get() = comboBox.model as? CommonComboBoxModel

    private val text: String
      get() = model?.text ?: ""
  }
}
