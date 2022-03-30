/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.rules

import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RulesTableModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class RulesTableView(private val model: NetworkInspectorModel) {
  val component: JComponent

  init {
    val tableModel = RulesTableModel()
    val table = TableView(tableModel)
    val decorator = ToolbarDecorator.createDecorator(table)
    component = createDecoratedTable(table, decorator)
    table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    table.selectionModel.addListSelectionListener {
      val row = table.selectedObject ?: return@addListSelectionListener
      model.setSelectedRule(row)
    }
  }
}
