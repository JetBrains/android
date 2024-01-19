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
package com.android.tools.property.panel.api

import com.android.tools.property.ptable.PTableModel
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.JComponent

/**
 * Interface of an inspector panel.
 *
 * The inspector panel is generated from [InspectorBuilder]s that each add rows to the inspector. A
 * row can be either:
 * - Title row
 * - Label & Editor Component
 * - A custom panel
 * - A separator line
 */
interface InspectorPanel {

  /** Add a title line to the inspector. */
  fun addTitle(title: String, actions: List<AnAction> = emptyList()): InspectorLineModel

  /** Add an expandable title line to the inspector. */
  fun addExpandableTitle(
    title: String,
    initiallyExpanded: Boolean = true,
    actions: List<AnAction> = emptyList(),
  ): InspectorLineModel {
    val line = addTitle(title, actions)
    line.makeExpandable(initiallyExpanded)
    return line
  }

  /** Add an expandable subtitle line to the inspector. */
  fun addSubTitle(
    title: String,
    initiallyExpanded: Boolean = true,
    parent: InspectorLineModel? = null,
  ): InspectorLineModel

  /**
   * Add an editor component with the property name for the label to the inspector.
   *
   * Add [editor] with model [editorModel] to the inspector, and return the new
   * [InspectorLineModel]. The editor is optionally placed as a child of expandable [parent] line.
   */
  fun addCustomEditor(
    editorModel: PropertyEditorModel,
    editor: JComponent,
    parent: InspectorLineModel? = null,
  ): InspectorLineModel

  /**
   * Add an editor component with the property name for the label to the inspector.
   *
   * Same as [addCustomEditor], but takes the model and editor as a pair. Add an editor with a model
   * as a [modelEditorPair] to the inspector, and return the new [InspectorLineModel]. The editor is
   * optionally placed as a child of expandable [parent] line.
   */
  fun addEditor(
    modelEditorPair: Pair<PropertyEditorModel, JComponent>,
    parent: InspectorLineModel? = null,
  ): InspectorLineModel {
    return addCustomEditor(modelEditorPair.first, modelEditorPair.second, parent)
  }

  /**
   * Add a table of properties to the inspector.
   *
   * Add a table with the items specified in [tableModel]. Specify if the table should be
   * [searchable] i.e. the user can search for the items in the table. Cell renderer and cell
   * editors must be specified in [tableUI]. The table is optionally placed as a child of expandable
   * [parent] line.
   */
  fun addTable(
    tableModel: PTableModel,
    searchable: Boolean,
    tableUI: TableUIProvider,
    actions: List<AnAction> = emptyList(),
    parent: InspectorLineModel? = null,
  ): TableLineModel

  /**
   * Adds a custom panel that spans the entire width.
   *
   * Add a [component] (usually a JPanel) to the inspector, and return the new [InspectorLineModel].
   * The component is optionally placed as a child of expandable [parent] line.
   */
  fun addComponent(component: JComponent, parent: InspectorLineModel? = null): InspectorLineModel
}
