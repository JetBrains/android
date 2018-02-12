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
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

const val NAV_ARGUMENTS_COMPONENT_NAME = "NavArgumentsPropertyInspector"
const val NAV_ARGUMENTS_ROW_HEIGHT = 22

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

      table.columnModel.getColumn(0).cellRenderer = MyCellRenderer("name")
      table.columnModel.getColumn(1).cellRenderer = MyCellRenderer("default value")

      val nameTextEditor = TextEditor(surface!!.project, false, NlEditingListener.DEFAULT_LISTENER)
      val defaultValueTextEditor = TextEditor(surface!!.project, false, NlEditingListener.DEFAULT_LISTENER)

      val nameEditor = NlTableCellEditor()
      nameEditor.init(nameTextEditor, null)
      val defaultValueEditor = NlTableCellEditor()
      defaultValueEditor.init(defaultValueTextEditor, null)

      table.columnModel.getColumn(0).cellEditor = nameEditor
      table.columnModel.getColumn(1).cellEditor = defaultValueEditor

      table.putClientProperty("terminateEditOnFocusLost", true)

      panel.add(table, BorderLayout.CENTER)
      val minus = InplaceButton("Delete Argument", deleteIcon) {
        table.cellEditor?.stopCellEditing()
        argumentProperty.deleteRows(table.selectedRows)
        tableModel.fireTableDataChanged()
      }
      val plus = InplaceButton("Add Argument", addIcon) {
        table.cellEditor?.stopCellEditing()
        argumentProperty.addRow()
        tableModel.fireTableDataChanged()
      }
      table.selectionModel.addListSelectionListener {
        minus.isEnabled = !table.selectedRows.isEmpty()
      }
      val plusPanel = JPanel(BorderLayout())
      val actionPanel = JPanel(FlowLayout())
      actionPanel.add(minus)
      actionPanel.add(plus)
      actionPanel.isOpaque = false
      plusPanel.add(actionPanel, BorderLayout.EAST)

      inspector.addExpandableComponent("Arguments", null, plusPanel, plusPanel)
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
      if (isSelected) {
        it.foreground = table?.selectionForeground
        it.background = table?.selectionBackground
      }
      else {
        it.foreground = table?.foreground
        it.background = table?.background
      }
    }
  }
}

private val deleteIcon = ColoredIconGenerator.generateColoredIcon(AllIcons.General.Remove, JBColor.GRAY.rgb)
private val addIcon = ColoredIconGenerator.generateColoredIcon(AllIcons.General.Add, JBColor.GRAY.rgb)