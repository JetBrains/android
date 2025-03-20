/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleVariable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.testFramework.PlatformTestUtil
import javax.swing.JButton
import javax.swing.JTable

val RuleVariablesDialog.addAction
  get() = findAction("Add")

val RuleVariablesDialog.removeAction
  get() = findAction("Remove")

fun RuleVariablesDialog.findAction(text: String): AnAction {
  val toolbar = findInstanceOf<ActionToolbar>()
  PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
  return toolbar.actions.first { it.templateText?.contains(text) == true }
}

val RuleVariablesDialog.table
  get() = findInstanceOf<JTable>()

inline fun <reified T> RuleVariablesDialog.findInstanceOf(): T =
  TreeWalker(rootPane).descendants().first { it is T } as T

fun RuleVariablesDialog.clickOk() {
  val ui = FakeUi(rootPane)
  ui.clickOn(ui.getComponent<JButton> { button -> button.text == "OK" })
}

fun RuleVariablesDialog.clickCancel() {
  val ui = FakeUi(rootPane)
  ui.clickOn(ui.getComponent<JButton> { button -> button.text == "Cancel" })
}

fun RuleVariablesDialog.variableAt(row: Int) =
  RuleVariable(table.model.getValueAt(row, 0) as String, table.model.getValueAt(row, 1) as String)

fun RuleVariablesDialog.variables() = buildList { repeat(table.rowCount) { add(variableAt(it)) } }

fun RuleVariablesDialog.setName(row: Int, name: String) {
  table.model.setValueAt(name, row, 0)
}

fun RuleVariablesDialog.setValue(row: Int, value: String) {
  table.model.setValueAt(value, row, 1)
}

fun RuleVariablesDialog.selectRow(row: Int) {
  table.changeSelection(row, /* columnIndex= */ 0, /* toggle= */ false, /* extend= */ false)
}
