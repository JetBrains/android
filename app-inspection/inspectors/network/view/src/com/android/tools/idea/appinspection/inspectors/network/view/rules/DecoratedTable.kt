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

import com.android.tools.adtui.TabularLayout
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * A Table class with decorated toolbar for network inspector.
 */
class DecoratedTable<Item>(model: ListTableModel<Item>) {
  val component = JPanel(TabularLayout("*", "Fit,Fit,*"))
  val table: TableView<Item>

  init {
    table = TableView(model)
    val decorator = ToolbarDecorator.createDecorator(table)
    component.add(decorator.createPanel().apply {
      border = BorderFactory.createEmptyBorder()
    }, TabularLayout.Constraint(0, 0))
    component.add(table.tableHeader, TabularLayout.Constraint(1, 0))
    component.add(table, TabularLayout.Constraint(2, 0))
  }
}
