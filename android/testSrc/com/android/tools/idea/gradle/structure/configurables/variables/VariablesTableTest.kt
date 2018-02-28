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
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
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
    val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariablesTable.VariableNode).name } as VariablesTable.VariableNode
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
    val variableNode = appNode.children().asSequence().find { "varBool" == (it as VariablesTable.VariableNode).name } as VariablesTable.VariableNode
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

    val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariablesTable.VariableNode).name } as VariablesTable.VariableNode
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

    val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariablesTable.VariableNode).name } as VariablesTable.VariableNode
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
}