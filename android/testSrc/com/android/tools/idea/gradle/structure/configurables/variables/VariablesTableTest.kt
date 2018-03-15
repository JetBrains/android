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
package com.android.tools.idea.gradle.structure.configurables.variables

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class VariablesTableTest : AndroidGradleTestCase() {

  fun testModuleNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild
    assertThat(tableModel.getValueAt(appNode, 0) as String, equalTo("app"))
    assertThat(tableModel.getValueAt(appNode, 1) as String, equalTo(""))
  }

  fun testStringVariableNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as DefaultMutableTreeNode
    val variableNode =
      appNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))

    assertThat(variableNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.STRING))
    assertThat(variableNode.childCount, equalTo(0))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("anotherVariable"))
    assertThat(tableModel.getValueAt(variableNode, 1) as String, equalTo("\"3.0.1\""))
  }

  fun testBooleanVariableNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as DefaultMutableTreeNode
    val variableNode =
      appNode.children().asSequence().find { "varBool" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))

    assertThat(variableNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.BOOLEAN))
    assertThat(variableNode.childCount, equalTo(0))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("varBool"))
    assertThat(tableModel.getValueAt(variableNode, 1) as String, equalTo("true"))
  }

  fun testListNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as DefaultMutableTreeNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode =
      appNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(listNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.LIST))
    assertThat(listNode.childCount, equalTo(2))
    assertThat(tableModel.getValueAt(listNode, 0) as String, equalTo("varProGuardFiles"))
    assertThat(tableModel.getValueAt(listNode, 1) as String, equalTo("[proguard-rules.txt, proguard-rules2.txt]"))

    variablesTable.tree.expandPath(TreePath(listNode.path))
    assertThat(tableModel.getValueAt(listNode, 0) as String, equalTo("varProGuardFiles"))
    assertThat(tableModel.getValueAt(listNode, 1) as String, equalTo(""))

    val firstElementNode = listNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"proguard-rules.txt\""))

    val secondElementNode = listNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo("1"))
    assertThat(tableModel.getValueAt(secondElementNode, 1) as String, equalTo("\"proguard-rules2.txt\""))
  }

  fun testMapNodeDisplay() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as DefaultMutableTreeNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode =
      appNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(mapNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.MAP))
    assertThat(mapNode.childCount, equalTo(2))
    assertThat(tableModel.getValueAt(mapNode, 0) as String, equalTo("mapVariable"))
    assertThat(tableModel.getValueAt(mapNode, 1) as String, equalTo("[a=\"double\" quotes, b='single' quotes]"))

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    assertThat(tableModel.getValueAt(mapNode, 0) as String, equalTo("mapVariable"))
    assertThat(tableModel.getValueAt(mapNode, 1) as String, equalTo(""))

    val firstElementNode = mapNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"\"double\" quotes\""))

    val secondElementNode = mapNode.getChildAt(1)
    assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo("b"))
    assertThat(tableModel.getValueAt(secondElementNode, 1) as String, equalTo("\"'single' quotes\""))
  }

  fun testModuleNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild
    assertThat(tableModel.isCellEditable(appNode, 0), equalTo(false))
  }

  fun testVariableNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    val variableNode =
      appNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("anotherVariable"))
    assertThat(tableModel.isCellEditable(variableNode, 0), equalTo(true))

    tableModel.setValueAt("renamed", variableNode, 0)
    assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("renamed"))

    appNode.module.applyChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    val variableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
    assertThat(variableNames, hasItem("renamed"))
    assertThat(variableNames, not(hasItem("anotherVariable")))
  }

  fun testListNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as DefaultMutableTreeNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode =
      appNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(listNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.LIST))

    variablesTable.tree.expandPath(TreePath(listNode.path))
    val firstElementNode = listNode.getChildAt(0)
    assertThat(tableModel.isCellEditable(firstElementNode, 0), equalTo(false))
  }

  fun testMapNodeRename() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode =
      appNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(mapNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.MAP))

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    val firstElementNode = mapNode.getChildAt(0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
    assertThat(tableModel.isCellEditable(firstElementNode, 0), equalTo(true))

    tableModel.setValueAt("renamed", firstElementNode, 0)
    assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("renamed"))

    appNode.module.applyChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    val newMapNode =
      newAppNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    val keyNames = newMapNode.children().asSequence().map { it.toString() }.toList()
    assertThat(keyNames, hasItem("renamed"))
    assertThat(keyNames, not(hasItem("a")))
  }

  fun testModuleNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild
    assertThat(tableModel.isCellEditable(appNode, 1), equalTo(false))
  }

  fun testVariableNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    val variableNode =
      appNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variablesTable.tree.expandPath(TreePath(variableNode.path))
    assertThat(tableModel.getValueAt(variableNode, 1) as String, equalTo("\"3.0.1\""))
    assertThat(tableModel.isCellEditable(variableNode, 1), equalTo(true))

    tableModel.setValueAt("new value", variableNode, 1)
    assertThat(tableModel.getValueAt(variableNode, 1) as String, equalTo("\"new value\""))

    appNode.module.applyChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    val newVariableNode =
      newAppNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(newVariableNode.getUnresolvedValue(false), equalTo("\"new value\""))
  }

  fun testListNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val listNode =
      appNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(listNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.LIST))
    assertThat(tableModel.isCellEditable(listNode, 1), equalTo(false))

    variablesTable.tree.expandPath(TreePath(listNode.path))
    val firstElementNode = listNode.getChildAt(0)
    assertThat(tableModel.isCellEditable(listNode, 1), equalTo(false))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"proguard-rules.txt\""))
    assertThat(tableModel.isCellEditable(firstElementNode, 1), equalTo(true))

    tableModel.setValueAt("new value", firstElementNode, 1)
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"new value\""))

    appNode.module.applyChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    val newListNode =
      newAppNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat((newListNode.getChildAt(0) as VariablesTable.ListItemNode).getUnresolvedValue(false), equalTo("\"new value\""))
  }

  fun testMapNodeSetValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    variablesTable.tree.expandPath(TreePath(appNode.path))

    val mapNode =
      appNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(mapNode.variable.valueType, equalTo(GradlePropertyModel.ValueType.MAP))
    assertThat(tableModel.isCellEditable(mapNode, 1), equalTo(false))

    variablesTable.tree.expandPath(TreePath(mapNode.path))
    val firstElementNode = mapNode.getChildAt(0)
    assertThat(tableModel.isCellEditable(mapNode, 1), equalTo(false))
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"\"double\" quotes\""))
    assertThat(tableModel.isCellEditable(firstElementNode, 1), equalTo(true))

    tableModel.setValueAt("new value", firstElementNode, 1)
    assertThat(tableModel.getValueAt(firstElementNode, 1) as String, equalTo("\"new value\""))

    appNode.module.applyChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    val newMapNode =
      newAppNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat((newMapNode.getChildAt(0) as VariablesTable.MapItemNode).getUnresolvedValue(false), equalTo("\"new value\""))
  }

  fun testAddSimpleVariable() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psContext = PsContext(PsProject(project), testRootDisposable)
    val variablesTable = VariablesTable(project, psContext)
    val tableModel = variablesTable.tableModel

    val appNode = (tableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newVariable")))

    variablesTable.tree.selectionPath = TreePath(appNode.path)
    variablesTable.addVariable(GradlePropertyModel.ValueType.STRING)
    val editorComp = variablesTable.editorComponent as JPanel
    val textBox = editorComp.components.first { it is VariableAwareTextBox } as VariableAwareTextBox
    textBox.text = "newVariable"
    variablesTable.editingStopped(null)

    val variableNode =
      appNode.children().asSequence().find { "newVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    variableNode.setValue("new value")

    appNode.module.applyChanges()
    val newTableModel = VariablesTable(project, psContext).tableModel
    val newAppNode = (newTableModel.root as DefaultMutableTreeNode).firstChild as VariablesTable.ModuleNode
    val newVariableNode =
      newAppNode.children().asSequence().find { "newVariable" == (it as VariablesTable.VariableNode).toString() } as VariablesTable.VariableNode
    assertThat(newVariableNode.getUnresolvedValue(false), equalTo("\"new value\""))
  }
}