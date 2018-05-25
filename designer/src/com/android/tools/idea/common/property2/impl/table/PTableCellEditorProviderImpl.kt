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
package com.android.tools.idea.common.property2.impl.table

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.ptable2.*
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.model.TableLineModel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent

class PTableCellEditorProviderImpl<P : PropertyItem>(private val itemType: Class<P>,
                                                     controlTypeProvider: ControlTypeProvider<P>,
                                                     editorProvider: EditorProvider<P>) : PTableCellEditorProvider {

  private val defaultEditor = DefaultPTableCellEditor()
  private val editor = PTableCellEditorImpl(controlTypeProvider, editorProvider)

  override fun invoke(table: PTable, property: PTableItem, column: PTableColumn): PTableCellEditor {
    if (column == PTableColumn.NAME || !itemType.isInstance(property)) {
      return defaultEditor
    }
    editor.nowEditing(table, itemType.cast(property))
    return editor
  }
}

class PTableCellEditorImpl<in P : PropertyItem>(
  private val controlTypeProvider: ControlTypeProvider<P>,
  private val editorProvider: EditorProvider<P>) : PTableCellEditor {

  private var property: P? = null
  private var table: PTable? = null
  private var model: PropertyEditorModel? = null
  private var controlType: ControlType? = null

  override var editorComponent: JComponent? = null
    private set

  override val value: String?
    get() = model?.value

  override val isBooleanEditor: Boolean
    get() = controlType == ControlType.THREE_STATE_BOOLEAN

  override fun toggleValue() {
    model?.toggleValue()
  }

  override fun requestFocus() {
    model?.requestFocus()
  }

  override fun cancelEditing() {
    model?.cancelEditing()
  }

  override fun close(oldTable: PTable) {
    if (table == oldTable) {
      property = null
      table = null
      model = null
      controlType = null
      editorComponent = null
    }
  }

  fun nowEditing(newTable: PTable, newProperty: P) {
    val (newModel, newEditor) = editorProvider.createEditor(newProperty, asTableCellEditor = true)
    val panel = AdtSecondaryPanel(BorderLayout())
    panel.add(newEditor, BorderLayout.CENTER)
    panel.border = JBUI.Borders.customLine(newTable.gridLineColor, 0, 1, 0, 0)
    newModel.onEnter = { startNextEditor() }

    property = newProperty
    table = newTable
    model = newModel
    controlType = controlTypeProvider(newProperty)
    editorComponent = panel
  }

  private fun startNextEditor() {
    val currentTable = table ?: return
    if (!currentTable.startNextEditor()) {
      val tableModel = currentTable.context as TableLineModel
      tableModel.gotoNextLine(tableModel)
    }
  }
}
