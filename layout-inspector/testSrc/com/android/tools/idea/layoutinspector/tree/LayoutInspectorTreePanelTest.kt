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

import com.android.ddmlib.testing.FakeAdbRule
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.model.WINDOW_MANAGER_FLAG_DIM_BEHIND
import com.android.tools.idea.layoutinspector.util.CheckUtil
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.view
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
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import javax.swing.JTree

@RunsInEdt
class LayoutInspectorTreePanelTest {
  private var componentStack: ComponentStack? = null
  private val projectRule = AndroidProjectRule.withSdk()
  private val adbRule = FakeAdbRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(adbRule).around(EdtRule())!!

  @Before
  fun setUp() {
    componentStack = ComponentStack(projectRule.project)
    componentStack!!.registerComponentInstance(FileEditorManager::class.java, Mockito.mock(FileEditorManager::class.java))
  }

  @After
  fun tearDown() {
    componentStack!!.restore()
    componentStack = null
  }

  @Test
  fun testGotoDeclaration() {
    val tree = LayoutInspectorTreePanel()
    val model = model(projectRule.project, DemoExample.setUpDemo(projectRule.fixture))
    val inspector = LayoutInspector(model, projectRule.fixture.projectDisposable)
    tree.setToolContext(inspector)
    tree.componentTreeSelectionModel.currentSelection = listOf(model["title"]!!)
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

  @Test
  fun testMultiWindow() {
    val tree = LayoutInspectorTreePanel()
    val model = InspectorModel(projectRule.project)
    val inspector = LayoutInspector(model, projectRule.fixture.projectDisposable)
    tree.setToolContext(inspector)
    val jtree = UIUtil.findComponentOfType(tree.component, JTree::class.java) as JTree
    model.update(view(ROOT) { view(VIEW1) }, ROOT, listOf(ROOT))
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(jtree.rowCount).isEqualTo(1)
    Truth.assertThat(jtree.getPathForRow(0).lastPathComponent).isEqualTo(model[ROOT])

    model.update(view(VIEW2) { view(VIEW3) }, VIEW2, listOf(ROOT, VIEW2))
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(jtree.rowCount).isEqualTo(2)
    Truth.assertThat(jtree.getPathForRow(1).lastPathComponent).isEqualTo(model[VIEW2])

    model.update(view(VIEW2, layoutFlags = WINDOW_MANAGER_FLAG_DIM_BEHIND) { view(VIEW3) }, VIEW2, listOf(ROOT, VIEW2))
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(jtree.rowCount).isEqualTo(3)
    Truth.assertThat((jtree.getPathForRow(1).lastPathComponent as ViewNode).qualifiedName).isEqualTo("DIM_BEHIND")
  }
}
