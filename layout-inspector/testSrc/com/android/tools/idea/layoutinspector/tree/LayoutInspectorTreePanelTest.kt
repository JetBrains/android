/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.idea.layoutinspector.util.CheckUtil
import com.android.tools.idea.layoutinspector.util.InspectorBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import javax.swing.JTree

@RunsInEdt
class LayoutInspectorTreePanelTest {
  private var componentStack: ComponentStack? = null

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Before
  fun setUp() {
    InspectorBuilder.setUpDemo(projectRule)
    componentStack = ComponentStack(projectRule.project)
    componentStack!!.registerComponentImplementation(FileEditorManager::class.java, Mockito.mock(FileEditorManager::class.java))
  }

  @After
  fun tearDown() {
    InspectorBuilder.tearDownDemo()
    componentStack!!.restoreComponents()
    componentStack = null
  }

  @Test
  fun testGotoDeclaration() {
    val tree = LayoutInspectorTreePanel()
    val inspector = InspectorBuilder.createLayoutInspectorForDemo(projectRule)
    tree.setToolContext(inspector)
    tree.componentTreeSelectionModel.selection = listOf(InspectorBuilder.findViewNode(inspector, "title")!!)
    val treeComponent = UIUtil.findComponentOfType(tree.component, JTree::class.java)

    val fileManager = FileEditorManager.getInstance(projectRule.project)
    val file = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
    Mockito.`when`(fileManager.openEditor(ArgumentMatchers.any(OpenFileDescriptor::class.java), ArgumentMatchers.anyBoolean()))
      .thenReturn(listOf(Mockito.mock(FileEditor::class.java)))

    val action = treeComponent!!.actionMap[GOTO_DEFINITION_ACTION_KEY]
    action.actionPerformed(null)
    Mockito.verify(fileManager).openEditor(file.capture(), ArgumentMatchers.eq(true))
    val descriptor = file.value

    Truth.assertThat(descriptor.file.name).isEqualTo("demo.xml")
    Truth.assertThat(CheckUtil.findLineAtOffset(descriptor.file, descriptor.offset)).isEqualTo("<TextView")
  }
}
