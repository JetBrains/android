/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.NlComponentEditor
import com.android.tools.idea.common.property.inspector.InspectorComponent
import com.android.tools.idea.common.property.inspector.InspectorPanel
import com.android.tools.idea.common.property.inspector.InspectorProvider
import com.android.tools.idea.naveditor.property.NavDestinationArgumentsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.editors.TextEditor
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.android.tools.idea.uibuilder.property.editors.NlTableCellEditor
import com.intellij.icons.AllIcons
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

const val NAV_ARGUMENTS_COMPONENT_NAME = "NavArgumentsPropertyInspector"
val NAV_ARGUMENTS_ROW_HEIGHT = JBUI.scale(22)

class NavDestinationArgumentsInspectorProvider : InspectorProvider<NavPropertiesManager> {

  private var inspector: NavArgumentsInspectorComponent? = null

  override fun isApplicable(components: List<NlComponent>,
                            properties: Map<String, NlProperty>,
                            propertiesManager: NavPropertiesManager): Boolean {
    if (components.size != 1) {
      return false
    }
    if (properties.values.none { it is NavDestinationArgumentsProperty }) {
      return false
    }

    return true
  }

  override fun createCustomInspector(components: List<NlComponent>,
                                     properties: Map<String, NlProperty>,
                                     propertiesManager: NavPropertiesManager): InspectorComponent<NavPropertiesManager> {
    val inspector = inspector ?: NavArgumentsInspectorComponent()
    this.inspector = inspector

    inspector.updateProperties(components, properties, propertiesManager)
    return inspector
  }

  override fun resetCache() {
    inspector = null
  }

  private class NavArgumentsInspectorComponent :
      InspectorComponent<NavPropertiesManager> {

    private lateinit var argumentProperty: NavDestinationArgumentsProperty
    private val components = mutableListOf<NlComponent>()
    private var surface: NavDesignSurface? = null

    override fun updateProperties(components: List<NlComponent>,
                                  properties: Map<String, NlProperty>,
                                  propertiesManager: NavPropertiesManager) {
      this.components.clear()
      this.components.addAll(components)

      surface = propertiesManager.designSurface as? NavDesignSurface

      argumentProperty = properties.values.filterIsInstance(NavDestinationArgumentsProperty::class.java).first()
      refresh()
    }

    override fun getEditors(): List<NlComponentEditor> = listOf()

    override fun getMaxNumberOfRows() = 2

    override fun attachToInspector(inspector: InspectorPanel<NavPropertiesManager>) {
      val panel = JPanel(BorderLayout())
      val tableModel = NavArgumentsTableModel(argumentProperty)
      val table = JBTable(tableModel)
      table.rowHeight = NAV_ARGUMENTS_ROW_HEIGHT
      table.name = NAV_ARGUMENTS_COMPONENT_NAME
      table.rowSelectionAllowed = true
      table.minimumSize = Dimension(0, JBUI.scale(18))
      table.emptyText.also {
        it.text = "Click "
        it.appendText("+", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        it.appendText(" to add Arguments")
      }
      val textEditor = TextEditor(surface!!.project, false, NlEditingListener.DEFAULT_LISTENER)

      table.addKeyListener(object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent?) {
          when (e?.keyCode) {
            KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> {
              argumentProperty.deleteRows(table.selectedRows)
              refresh()
              tableModel.fireTableDataChanged()
            }
            KeyEvent.VK_TAB -> {
              table.editCellAt(table.selectedRow, table.selectedColumn)
              textEditor.requestFocus()
            }
          }
        }
      })
      table.columnModel.getColumn(0).cellRenderer = MyCellRenderer("name")
      table.columnModel.getColumn(1).cellRenderer = MyCellRenderer("default value")

      val cellEditor = object: NlTableCellEditor() {
        override fun isCellEditable(e: EventObject?): Boolean {
          return super.isCellEditable(e) && (e !is MouseEvent || e.clickCount == 2)
        }
      }
      cellEditor.init(textEditor, null)

      table.columnModel.getColumn(0).cellEditor = cellEditor
      table.columnModel.getColumn(1).cellEditor = cellEditor

      panel.add(table, BorderLayout.CENTER)
      val plus = InplaceButton("Add Argument", addIcon) {
        table.cellEditor?.stopCellEditing()
        argumentProperty.addRow()
        tableModel.fireTableDataChanged()
      }
      inspector.addTitle("Arguments", plus)
      inspector.addPanel(panel)
    }

    override fun refresh() {
      argumentProperty.refreshList()
    }
  }
}

private class MyCellRenderer(emptyText: String) : TableCellRenderer {
  val rendererComponent = JBTextField()
  init {
    rendererComponent.emptyText.text = emptyText
    rendererComponent.border = BorderFactory.createEmptyBorder()
  }

  override fun getTableCellRendererComponent(
    table: JTable?,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int
  ): Component {
    return rendererComponent.also {
      it.text = (value as? NlProperty)?.value
      if (isSelected && (table?.hasFocus() == true || table?.isEditing == true)) {
        it.foreground = table.selectionForeground
        it.background = table.selectionBackground
      }
      else {
        it.foreground = table?.foreground
        it.background = if (isSelected) UIUtil.getListUnfocusedSelectionBackground() else table?.background
      }
    }
  }
}

private val addIcon = ColoredIconGenerator.generateColoredIcon(AllIcons.General.Add, JBColor.GRAY.rgb)