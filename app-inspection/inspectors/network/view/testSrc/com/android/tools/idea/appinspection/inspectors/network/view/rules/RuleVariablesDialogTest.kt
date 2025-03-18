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
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleVariable
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import java.util.EventObject
import javax.swing.JButton
import javax.swing.JTable
import org.junit.Rule
import org.junit.Test

@Suppress("OverrideOnly")
@RunsInEdt
class RuleVariablesDialogTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, HeadlessDialogRule(), WaitForIndexRule(projectRule), EdtRule())

  private val project
    get() = projectRule.project

  private val variables = mutableListOf<RuleVariable>()

  @Test
  fun addVariable() {
    val dialog = ruleVariablesDialog()
    createModalDialogAndInteractWithIt(dialog::show) {
      it.addAction.actionPerformed(TestActionEvent.createTestEvent())

      assertThat(it.variables()).containsExactly(RuleVariable("NEW-VARIABLE", ""))
      assertThat(it.table.getCellEditor(0, 0).isCellEditable(EventObject("")))
      assertThat(it.table.getCellEditor(0, 1).isCellEditable(EventObject("")))
    }
  }

  @Test
  fun addVariableAndApply() {
    val dialog = ruleVariablesDialog(variables)
    createModalDialogAndInteractWithIt(dialog::show) {
      it.addAction.actionPerformed(TestActionEvent.createTestEvent())
      it.clickOk()
    }

    assertThat(variables).containsExactly(RuleVariable("NEW-VARIABLE", ""))
  }

  @Test
  fun addVariableAndCancel() {
    val dialog = ruleVariablesDialog(variables)
    createModalDialogAndInteractWithIt(dialog::show) {
      it.addAction.actionPerformed(TestActionEvent.createTestEvent())
      it.clickCancel()
    }

    assertThat(variables).isEmpty()
  }

  @Test
  fun addVariable_multipleTimes() {
    val dialog = ruleVariablesDialog()
    createModalDialogAndInteractWithIt(dialog::show) {
      it.addAction.actionPerformed(TestActionEvent.createTestEvent())
      it.addAction.actionPerformed(TestActionEvent.createTestEvent())
      it.addAction.actionPerformed(TestActionEvent.createTestEvent())

      assertThat(it.variables())
        .containsExactly(
          RuleVariable("NEW-VARIABLE", ""),
          RuleVariable("NEW-VARIABLE-2", ""),
          RuleVariable("NEW-VARIABLE-3", ""),
        )
    }
  }

  @Test
  fun editVariableAndApply() {
    val dialog = ruleVariablesDialog(variables = variables)
    createModalDialogAndInteractWithIt(dialog::show) {
      it.addAction.actionPerformed(TestActionEvent.createTestEvent())

      it.setName(0, "NAME")
      it.setValue(0, "Foo")
      it.clickOk()

      assertThat(variables).containsExactly(RuleVariable("NAME", "Foo"))
    }
  }

  @Test
  fun removeVariableAndApply() {
    variables.add(RuleVariable("FOO", "foo"))
    variables.add(RuleVariable("BAR", "bar"))

    val dialog = ruleVariablesDialog(variables = variables)
    createModalDialogAndInteractWithIt(dialog::show) {
      it.selectRow(1)
      it.removeAction.actionPerformed(TestActionEvent.createTestEvent())
      it.clickOk()
    }
    assertThat(variables).containsExactly(RuleVariable("FOO", "foo"))
  }

  @Test
  fun editVariable_duplicateName() {
    val dialog = ruleVariablesDialog()
    createModalDialogAndInteractWithIt(dialog::show) {
      it.addAction.actionPerformed(TestActionEvent.createTestEvent())
      it.addAction.actionPerformed(TestActionEvent.createTestEvent())

      it.setName(0, "NAME")
      it.setName(1, "NAME")

      assertThat(dialog.doValidate()).isEqualTo(ValidationInfo("Duplicate variable names: NAME"))
    }
  }

  @Test
  fun rendersVariables() {
    variables.add(RuleVariable("FOO", "foo"))
    variables.add(RuleVariable("BAR", "bar"))
    val dialog = ruleVariablesDialog(variables)
    createModalDialogAndInteractWithIt(dialog::show) {
      assertThat(it.variables())
        .containsExactly(RuleVariable("FOO", "foo"), RuleVariable("BAR", "bar"))
    }
  }

  @Test
  fun updatesRules() {
    variables.add(RuleVariable("FOO", "foo"))
    variables.add(RuleVariable("BAR", "bar"))
    val rule1 = ruleWithVariable(1, "FOO")
    val rule2 = ruleWithVariable(2, "BAR")
    val rule3 = ruleWithoutVariable(3)

    val rules = listOf(rule1, rule2, rule3)
    val updatedRules = mutableListOf<RuleData>()

    val dialog = ruleVariablesDialog(variables, rules) { updatedRules.add(it) }
    createModalDialogAndInteractWithIt(dialog::show) {
      it.setValue(0, "foo1")
      it.setValue(1, "bar1")
      it.clickOk()
    }
    assertThat(updatedRules).containsExactly(rule1, rule2)
  }

  private fun ruleVariablesDialog(
    variables: MutableList<RuleVariable> = mutableListOf(),
    rules: List<RuleData> = mutableListOf(),
    onRulesUpdated: (RuleData) -> Unit = {},
  ) = RuleVariablesDialog(project, variables, rules, onRulesUpdated)
}

private val DialogWrapper.addAction
  get() = findAction("Add")

private val DialogWrapper.removeAction
  get() = findAction("Remove")

private fun DialogWrapper.findAction(text: String): AnAction {
  val toolbar = findInstanceOf<ActionToolbar>()
  PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
  return toolbar.actions.first { it.templateText?.contains(text) == true }
}

private val DialogWrapper.table
  get() = findInstanceOf<JTable>()

private inline fun <reified T> DialogWrapper.findInstanceOf(): T =
  TreeWalker(rootPane).descendants().first { it is T } as T

private fun DialogWrapper.clickOk() {
  val ui = FakeUi(rootPane)
  ui.clickOn(ui.getComponent<JButton> { button -> button.text == "OK" })
}

private fun DialogWrapper.clickCancel() {
  val ui = FakeUi(rootPane)
  ui.clickOn(ui.getComponent<JButton> { button -> button.text == "Cancel" })
}

private fun DialogWrapper.variableAt(row: Int) =
  RuleVariable(table.model.getValueAt(row, 0) as String, table.model.getValueAt(row, 1) as String)

private fun DialogWrapper.variables() = buildList { repeat(table.rowCount) { add(variableAt(it)) } }

private fun DialogWrapper.setName(row: Int, name: String) {
  table.model.setValueAt(name, row, 0)
}

private fun DialogWrapper.setValue(row: Int, value: String) {
  table.model.setValueAt(value, row, 1)
}

private fun DialogWrapper.selectRow(row: Int) {
  table.changeSelection(row, /* columnIndex= */ 0, /* toggle= */ false, /* extend= */ false)
}

private fun ruleWithVariable(id: Int, variable: String): RuleData {
  return ruleWithoutVariable(id).apply { criteria.host = "\${$variable}" }
}

private fun ruleWithoutVariable(id: Int): RuleData {
  return RuleData(id, "rule-$id", true)
}
