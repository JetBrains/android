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

import com.android.tools.idea.layoutinspector.LayoutInspectorTransportRule
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.WINDOW_MANAGER_FLAG_DIM_BEHIND
import com.android.tools.idea.layoutinspector.util.CheckUtil
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
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
  private val inspectorRule = LayoutInspectorTransportRule(projectRule = AndroidProjectRule.withSdk())
    .withDefaultDevice()
    .withDemoLayout()
    .attach()

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectorRule).around(EdtRule())!!

  private var componentStack: ComponentStack? = null

  @Before
  fun setUp() {
    val fileManager = Mockito.mock(FileEditorManager::class.java)
    Mockito.`when`(fileManager.openFiles).thenReturn(VirtualFile.EMPTY_ARRAY)
    componentStack = ComponentStack(inspectorRule.project)
    componentStack!!.registerComponentInstance(FileEditorManager::class.java, fileManager)
  }

  @After
  fun tearDown() {
    componentStack!!.restore()
    componentStack = null
  }

  @Test
  fun testGotoDeclaration() {
    val tree = LayoutInspectorTreePanel()
    val model = inspectorRule.inspectorModel
    val inspector = inspectorRule.inspector
    tree.setToolContext(inspector)
    model.selection = model["title"]
    val treeComponent = UIUtil.findComponentOfType(tree.component, JTree::class.java)

    val fileManager = FileEditorManager.getInstance(inspectorRule.project)
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
    val model = inspectorRule.inspectorModel
    val inspector = inspectorRule.inspector
    tree.setToolContext(inspector)
    val jtree = UIUtil.findComponentOfType(tree.component, JTree::class.java) as JTree
    model.update(window(ROOT, ROOT) { view(VIEW1) }, listOf(ROOT), 0)
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(jtree.rowCount).isEqualTo(1)
    Truth.assertThat(jtree.getPathForRow(0).lastPathComponent).isEqualTo(model[ROOT])

    model.update(window(VIEW2, VIEW2) { view(VIEW3) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(jtree.rowCount).isEqualTo(2)
    Truth.assertThat(jtree.getPathForRow(1).lastPathComponent).isEqualTo(model[VIEW2])

    model.update(window(VIEW2, VIEW2, layoutFlags = WINDOW_MANAGER_FLAG_DIM_BEHIND) { view(VIEW3) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    // Still 2: the dimmer is drawn but isn't in the tree
    Truth.assertThat(jtree.rowCount).isEqualTo(2)
  }
}
