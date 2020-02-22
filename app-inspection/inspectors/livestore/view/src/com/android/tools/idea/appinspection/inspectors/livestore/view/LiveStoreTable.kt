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
package com.android.tools.idea.appinspection.inspectors.livestore.view

import com.android.tools.appinspection.livestore.protocol.IdeHint
import com.android.tools.appinspection.livestore.protocol.LiveStoreDefinition
import com.android.tools.appinspection.livestore.protocol.ValueDefinition
import com.android.tools.appinspection.livestore.protocol.ValueType
import com.android.tools.idea.appinspection.inspectors.livestore.model.LiveStoreInspectorClient
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColorChooser
import com.intellij.ui.ColorPickerListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.ColorIcon
import java.awt.Color
import java.awt.Component
import javax.swing.AbstractCellEditor
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.event.CellEditorListener
import javax.swing.event.ChangeEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellEditor

private const val STORE_KEY = "store"

/**
 * A table component for showing the details of a single livestore.
 *
 * See also: [setContentsTo]
 *
 * This class also hosts many custom cell editors for rendering the different possible types of
 * livestore values.
 */
class LiveStoreTable(private val client: LiveStoreInspectorClient) {

  /**
   * Base livestore value editor, which handles communicating updates to the device.
   */
  private abstract class AbstractValueEditor<C : JComponent, T>(private val client: LiveStoreInspectorClient)
    : TableCellEditor, AbstractCellEditor() {
    private lateinit var table: JTable
    private var row: Int = 0
    private lateinit var lastValueDefn: ValueDefinition
    protected abstract val component: C

    abstract fun updateComponent(valueDefinition: ValueDefinition)
    abstract fun getValue(component: C): T

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component {
      this.table = table
      this.row = row
      lastValueDefn = value as ValueDefinition
      updateComponent(lastValueDefn)

      return component
    }

    override fun getCellEditorValue(): Any {
      // The value will be stale for a moment but will soon be updated after device-side confirmation
      return lastValueDefn
    }

    protected fun sendValueUpdateToDevice() {
      val newValue = getValue(component).toString()
      val store = table.getClientProperty(STORE_KEY) as LiveStoreDefinition
      val keyName = table.model.getValueAt(row, 0) as String
      client.requestValueUpdate(store.name, keyName, newValue)
    }

    protected fun refreshCell() {
      (table.model as AbstractTableModel).fireTableCellUpdated(row, 1)
    }
  }

  private class StringValueEditor(client: LiveStoreInspectorClient) : AbstractValueEditor<JTextField, String>(client) {
    override val component = JBTextField().apply {
      addActionListener {
        stopCellEditing()
        sendValueUpdateToDevice()
      }
    }
    override fun updateComponent(valueDefinition: ValueDefinition) {
      component.apply { text = valueDefinition.value }
    }
    override fun getValue(component: JTextField) = component.text!!
  }

  private class BoolValueEditor(client: LiveStoreInspectorClient) : AbstractValueEditor<JCheckBox, Boolean>(client) {
    override val component = JBCheckBox().apply {
      addActionListener { sendValueUpdateToDevice() }
    }
    override fun updateComponent(valueDefinition: ValueDefinition) {
      component.apply {
        // Annoyingly, the cell immediately toggles the checkbox value; instead, lie about our
        // initial value, so the UI first shows the correct value
        isSelected = !valueDefinition.value.toBoolean()
      }
    }
    override fun getValue(component: JCheckBox) = component.isSelected
  }

  private class EnumValueEditor(private val client: LiveStoreInspectorClient) : AbstractValueEditor<JComboBox<String>, String>(client) {
    override val component = ComboBox<String>().apply {
      addActionListener {
        if (this.selectedItem != null) {
          sendValueUpdateToDevice()
        }
      }
    }

    override fun updateComponent(valueDefinition: ValueDefinition) {
      component.apply {
        val enumClassName = valueDefinition.constraint!!
        component.model = DefaultComboBoxModel(client.enumDefinitions.getValue(enumClassName).toTypedArray())
        component.selectedItem = valueDefinition.value
      }
    }

    override fun getValue(component: JComboBox<String>) = component.selectedItem as String
  }

  private class IntRangeValueEditor(client: LiveStoreInspectorClient) : AbstractValueEditor<JSlider, Int>(client) {
    override val component = JSlider().apply {
      addChangeListener {
        sendValueUpdateToDevice()
      }
    }
    override fun updateComponent(valueDefinition: ValueDefinition) {
      component.apply {
        valueDefinition.constraint!!.split(" .. ").apply {
          minimum = this[0].toInt()
          maximum = this[1].toInt()
        }
        value = valueDefinition.value.toInt()
      }
    }

    override fun getValue(component: JSlider) = component.value
  }

  private class FloatRangeValueEditor(client: LiveStoreInspectorClient) : AbstractValueEditor<JSlider, Float>(client) {
    override val component = JSlider().apply {
      addChangeListener {
        sendValueUpdateToDevice()
      }
    }

    override fun updateComponent(valueDefinition: ValueDefinition) {
      component.apply {
        valueDefinition.constraint!!.split(" .. ").apply {
          minimum = (this[0].toFloat() * 100f).toInt()
          maximum = (this[1].toFloat() * 100f).toInt()
        }
        value = (valueDefinition.value.toFloat() * 100f).toInt()
      }
    }

    override fun getValue(component: JSlider) = component.value / 100f
  }

  private class ColorValueEditor(client: LiveStoreInspectorClient) : AbstractValueEditor<JButton, String>(client) {
    companion object {
      private val HEX_REGEX = "[0-9a-f]"
      val COLOR_REGEX = Regex("""#($HEX_REGEX$HEX_REGEX)($HEX_REGEX$HEX_REGEX)($HEX_REGEX$HEX_REGEX)""", RegexOption.IGNORE_CASE)

      private fun Int.toHexString() = String.format("%02X", this)
    }
    private fun String.toColor(): Color? {
      return COLOR_REGEX.matchEntire(this)
       ?.let { match ->
         val r = match.groupValues[1].toInt(16)
         val g = match.groupValues[2].toInt(16)
         val b = match.groupValues[3].toInt(16)
         Color(r, g, b)
       }
    }

    private var lastColor = Color.BLACK
    override val component = JButton().apply {
      horizontalAlignment = SwingConstants.LEFT
      addActionListener {
        val prevColor = lastColor
        val colorListener = object : ColorPickerListener {
          override fun colorChanged(color: Color) {
            lastColor = color
            sendValueUpdateToDevice()
            refreshCell()
          }

          override fun closed(color: Color?) {
            if (color == null) {
              lastColor = prevColor
              sendValueUpdateToDevice()
              refreshCell()
            }
          }
        }
        ColorChooser.chooseColor(this, "Choose a color", lastColor, false, listOf(colorListener), false)
      }
    }

    override fun updateComponent(valueDefinition: ValueDefinition) {
      valueDefinition.value.toColor()?.let { color ->
        lastColor = color
        component.icon = ColorIcon(16, color)
        component.text = valueDefinition.value
      }
    }

    override fun getValue(component: JButton)
      = "#${lastColor.red.toHexString()}${lastColor.green.toHexString()}${lastColor.blue.toHexString()}"
  }

  /**
   * Cell editor that delegates to more specific editors.
   *
   * This class needs to exist, unfortunately, because the [JTable] API only allows for one cell
   * editor type per column, but we really want a custom editor per row.
   */
  private class ValueEditor(client: LiveStoreInspectorClient) : AbstractTableCellEditor() {
    private lateinit var activeEditor: TableCellEditor
    private val stringEditor = StringValueEditor(client)
    private val boolEditor = BoolValueEditor(client)
    private val intRangeEditor = IntRangeValueEditor(client)
    private val floatRangeEditor = FloatRangeValueEditor(client)
    private val enumEditor = EnumValueEditor(client)
    private val colorEditor = ColorValueEditor(client)

    init {
      val editorListener = object : CellEditorListener {
        override fun editingStopped(e: ChangeEvent) = fireEditingStopped()
        override fun editingCanceled(e: ChangeEvent?) = fireEditingCanceled()
      }
      stringEditor.addCellEditorListener(editorListener)
      boolEditor.addCellEditorListener(editorListener)
      intRangeEditor.addCellEditorListener(editorListener)
      floatRangeEditor.addCellEditorListener(editorListener)
      enumEditor.addCellEditorListener(editorListener)
      colorEditor.addCellEditorListener(editorListener)
    }

    override fun getTableCellEditorComponent(table: JTable,
                                             value: Any,
                                             isSelected: Boolean,
                                             row: Int,
                                             column: Int): Component {
      val valueDefinition = value as ValueDefinition
      activeEditor = when (valueDefinition.type) {
        ValueType.INT -> if (valueDefinition.constraint != null) intRangeEditor else stringEditor
        ValueType.FLOAT -> if (valueDefinition.constraint != null) floatRangeEditor else stringEditor
        ValueType.BOOL -> boolEditor
        ValueType.ENUM -> enumEditor
        ValueType.CUSTOM -> {
          val hint = valueDefinition.constraint?.let { constraint ->
            try { IdeHint.valueOf(constraint) } catch (ex: IllegalArgumentException) { null }
          } ?: IdeHint.NONE

          if (hint == IdeHint.COLOR && ColorValueEditor.COLOR_REGEX.matches(valueDefinition.value)) {
            colorEditor
          }
          else {
            stringEditor
          }
        }

        else -> stringEditor
      }

      return activeEditor.getTableCellEditorComponent(table, value, isSelected, row, column)
    }

    override fun getCellEditorValue(): Any = activeEditor.cellEditorValue
  }

  private class ValueRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable,
                                               value: Any,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {
      val valueDefinition = value as ValueDefinition
      val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
      label.text = valueDefinition.value
      return label
    }
  }


  private val storeTableModel = object : DefaultTableModel(arrayOf("Key", "Value"), 0) {
    override fun isCellEditable(row: Int, column: Int): Boolean {
      return column == 1
    }
  }
  private val storeTable = JBTable(storeTableModel).apply {
    columnModel.getColumn(1).apply {
      cellRenderer = ValueRenderer()
      cellEditor = ValueEditor(client)
    }
  }
  private val root = JBScrollPane(storeTable)
  val component: JComponent = root

  init {
    client.addValueChangedListener { args ->
      for (row in 0 until storeTableModel.rowCount) {
        if (storeTableModel.getValueAt(row, 0) == args.keyName) {
          storeTableModel.fireTableCellUpdated(row, 0)
          break
        }
      }
    }
  }

  /**
   * Reset the contents of this table, populating it with the key / values from the target [store].
   */
  fun setContentsTo(store: LiveStoreDefinition) {
    if (storeTable.isEditing) {
      storeTable.cellEditor.cancelCellEditing()
    }
    storeTable.putClientProperty(STORE_KEY, store)
    storeTableModel.rowCount = 0
    store.keyValues.forEach { keyValue ->
      storeTableModel.addRow(arrayOf(keyValue.name, keyValue.value))
    }
  }
}