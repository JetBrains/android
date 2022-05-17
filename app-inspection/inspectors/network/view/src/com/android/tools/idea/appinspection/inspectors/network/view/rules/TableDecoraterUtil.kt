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
import com.intellij.util.ui.JBUI
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.KeyStroke

const val ENTER_ACTION_KEY = "ENTER_ACTION"

/**
 * Returns a table component with decorated toolbar.
 */
fun <Item> createDecoratedTable(table: TableView<Item>, decorator: ToolbarDecorator) =
  JPanel(TabularLayout("*", "Fit,Fit,*")).apply {
    add(decorator.createPanel().apply {
      border = JBUI.Borders.empty()
    }, TabularLayout.Constraint(0, 0))
    add(table.tableHeader, TabularLayout.Constraint(1, 0))
    add(table, TabularLayout.Constraint(2, 0))
  }

fun JTable.registerEnterKeyAction(action: (ActionEvent) -> Unit) {
  val enterKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
  getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enterKeyStroke, ENTER_ACTION_KEY)
  actionMap.put(ENTER_ACTION_KEY, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) = action(e)
  })
}
