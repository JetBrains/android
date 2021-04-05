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

import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.FQCN_RELATIVE_LAYOUT
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.flags.junit.SetFlagRule
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.adtui.workbench.ToolWindowCallback
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.compose
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.FLAG_HAS_MERGED_SEMANTICS
import com.android.tools.idea.layoutinspector.model.FLAG_HAS_UNMERGED_SEMANTICS
import com.android.tools.idea.layoutinspector.model.FLAG_SYSTEM_DEFINED
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.VIEW4
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.model.WINDOW_MANAGER_FLAG_DIM_BEHIND
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.transport.TransportInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.transport.addComponentTreeEvent
import com.android.tools.idea.layoutinspector.util.CheckUtil
import com.android.tools.idea.layoutinspector.util.DECOR_VIEW
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.JTree

private const val SYSTEM_PKG = -1
private const val USER_PKG = 123

private val PROCESS = MODERN_DEVICE.createProcess()

@RunsInEdt
class LayoutInspectorTreePanelTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val transportRule = TransportInspectorRule()
  private val inspectorRule = LayoutInspectorRule(transportRule.createClientProvider(), projectRule) { listOf(PROCESS.name) }
  private val setFlagRule = SetFlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS, true)

  @get:Rule
  val ruleChain = RuleChain.outerRule(transportRule).around(inspectorRule).around(setFlagRule).around((EdtRule()))!!

  private var componentStack: ComponentStack? = null

  @Before
  fun setUp() {
    val fileManager = Mockito.mock(FileEditorManagerEx::class.java)
    `when`(fileManager.selectedEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    `when`(fileManager.openFiles).thenReturn(VirtualFile.EMPTY_ARRAY)
    `when`(fileManager.allEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    componentStack = ComponentStack(inspectorRule.project)
    componentStack!!.registerComponentInstance(FileEditorManager::class.java, fileManager)

    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())
    inspectorRule.processes.selectedProcess = PROCESS
    transportRule.addComponentTreeEvent(inspectorRule, DemoExample.extractViewRoot(projectRule.fixture))
  }

  @After
  fun tearDown() {
    componentStack!!.restore()
    componentStack = null
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null)
  }

  @Test
  fun testGotoDeclaration() {
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val model = inspectorRule.inspectorModel
    val inspector = inspectorRule.inspector
    setToolContext(tree, inspector)

    model.setSelection(model["title"], SelectionOrigin.INTERNAL)

    val fileManager = FileEditorManager.getInstance(inspectorRule.project)
    val file = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
    `when`(fileManager.openEditor(ArgumentMatchers.any(OpenFileDescriptor::class.java), ArgumentMatchers.anyBoolean()))
      .thenReturn(listOf(Mockito.mock(FileEditor::class.java)))

    val focusManager = Mockito.mock(KeyboardFocusManager::class.java)
    `when`(focusManager.focusOwner).thenReturn(tree.component)
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)

    val dispatcher = IdeKeyEventDispatcher(null)
    val modifier = if (SystemInfo.isMac) KeyEvent.META_DOWN_MASK else KeyEvent.CTRL_DOWN_MASK
    dispatcher.dispatchKeyEvent(KeyEvent(tree.component, KeyEvent.KEY_PRESSED, 0, modifier, KeyEvent.VK_B, 'B'))

    verify(fileManager).openEditor(file.capture(), ArgumentMatchers.eq(true))
    val descriptor = file.value

    assertThat(descriptor.file.name).isEqualTo("demo.xml")
    assertThat(CheckUtil.findLineAtOffset(descriptor.file, descriptor.offset)).isEqualTo("<TextView")
  }

  @Test
  fun testMultiWindowWithVisibleSystemNodes() {
    TreeSettings.hideSystemNodes = false
    val demo = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "demo")
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val model = inspectorRule.inspectorModel
    val inspector = inspectorRule.inspector
    setToolContext(tree, inspector)
    val jtree = UIUtil.findComponentOfType(tree.component, JTree::class.java) as JTree
    UIUtil.dispatchAllInvocationEvents()
    assertThat(jtree.rowCount).isEqualTo(1)
    assertThat(jtree.getPathForRow(0).lastPathComponent).isEqualTo(model[ROOT]!!.treeNode)
    assertThat(model[ROOT]!!.qualifiedName).isEqualTo(DECOR_VIEW)

    model.update(window(ROOT, ROOT) { view(VIEW4) { view(VIEW1, layout = demo) } }, listOf(ROOT), 0)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(jtree).blockingGet(1, TimeUnit.SECONDS)
    assertThat(jtree.rowCount).isEqualTo(3)
    assertThat(jtree.getPathForRow(0).lastPathComponent).isEqualTo(model[ROOT]!!.treeNode)
    assertThat(jtree.getPathForRow(1).lastPathComponent).isEqualTo(model[VIEW4]!!.treeNode)
    assertThat(jtree.getPathForRow(2).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)

    model.update(window(VIEW2, VIEW2) { view(VIEW3) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(jtree).blockingGet(1, TimeUnit.SECONDS)
    assertThat(jtree.rowCount).isEqualTo(5)
    assertThat(jtree.getPathForRow(0).lastPathComponent).isEqualTo(model[ROOT]!!.treeNode)
    assertThat(jtree.getPathForRow(1).lastPathComponent).isEqualTo(model[VIEW4]!!.treeNode)
    assertThat(jtree.getPathForRow(2).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)
    assertThat(jtree.getPathForRow(3).lastPathComponent).isEqualTo(model[VIEW2]!!.treeNode)
    assertThat(jtree.getPathForRow(4).lastPathComponent).isEqualTo(model[VIEW3]!!.treeNode)

    model.update(window(VIEW2, VIEW2, layoutFlags = WINDOW_MANAGER_FLAG_DIM_BEHIND) { view(VIEW3) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    // Still 5: the dimmer is drawn but isn't in the tree
    assertThat(jtree.rowCount).isEqualTo(5)
  }

  @Test
  fun testMultiWindowWithHiddenSystemNodes() {
    TreeSettings.hideSystemNodes = true
    val android = ResourceReference(ResourceNamespace.ANDROID, ResourceType.LAYOUT, "simple_screen")
    val demo = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "demo")
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val model = inspectorRule.inspectorModel
    val inspector = inspectorRule.inspector
    setToolContext(tree, inspector)
    val jtree = UIUtil.findComponentOfType(tree.component, JTree::class.java) as JTree
    UIUtil.dispatchAllInvocationEvents()
    assertThat(jtree.rowCount).isEqualTo(1)
    assertThat(jtree.getPathForRow(0).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)
    assertThat(model[VIEW1]!!.qualifiedName).isEqualTo(FQCN_RELATIVE_LAYOUT)

    // ROOT & VIEW4 are system views (no layout, android layout)
    model.update(window(ROOT, ROOT) { view(VIEW4, layout = android) { view(VIEW1, layout = demo) } }, listOf(ROOT), 0)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(jtree).blockingGet(1, TimeUnit.SECONDS)
    assertThat(jtree.rowCount).isEqualTo(1)
    assertThat(jtree.getPathForRow(0).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)

    model.update(window(VIEW2, VIEW2) { view(VIEW3, layout = demo) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(jtree).blockingGet(1, TimeUnit.SECONDS)
    assertThat(jtree.rowCount).isEqualTo(2)
    assertThat(jtree.getPathForRow(0).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)
    assertThat(jtree.getPathForRow(1).lastPathComponent).isEqualTo(model[VIEW3]!!.treeNode)

    model.update(window(VIEW2, VIEW2, layoutFlags = WINDOW_MANAGER_FLAG_DIM_BEHIND) { view(VIEW3, layout = demo) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    // Still 2: the dimmer is drawn but isn't in the tree
    assertThat(jtree.rowCount).isEqualTo(2)
  }

  @RunsInEdt
  @Test
  fun testSelectFromComponentTree() {
    TreeSettings.hideSystemNodes = false
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val model = inspectorRule.inspectorModel
    val inspector = inspectorRule.inspector
    setToolContext(tree, inspector)
    val jtree = tree.tree!!
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(jtree).blockingGet(10, TimeUnit.SECONDS)

    var selectedView: ViewNode? = null
    var selectionOrigin = SelectionOrigin.INTERNAL
    model.selectionListeners.add { _, newSelection, origin ->
      selectedView = newSelection
      selectionOrigin = origin
    }

    jtree.setSelectionRow(2)
    assertThat(selectionOrigin).isEqualTo(SelectionOrigin.COMPONENT_TREE)
    assertThat(selectedView?.drawId).isEqualTo(VIEW2)
    assertThat(selectedView?.qualifiedName).isEqualTo(FQCN_TEXT_VIEW)
  }

  @RunsInEdt
  @Test
  fun testFiltering() {
    TreeSettings.hideSystemNodes = false
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    val model = inspectorRule.inspectorModel
    setToolContext(tree, inspector)
    UIUtil.dispatchAllInvocationEvents()

    // The component tree is now collapsed with the top element: DecorView
    // Demo has 3 views: DecorView, RelativeLayout, TextView. We should be able to find views even if they are currently collapsed:
    tree.setFilter("View")
    UIUtil.dispatchAllInvocationEvents()
    var selection = tree.tree?.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree?.selectionRows?.asList()).containsExactly(0)
    assertThat(selection?.view?.qualifiedName).isEqualTo(DECOR_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate a down arrow keyboard event in the search field: the next match should be found (TextView)
    val searchField = JPanel()
    searchField.addKeyListener(tree.filterKeyListener)
    val ui = FakeUi(searchField)
    ui.keyboard.setFocus(searchField)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.tree?.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree?.selectionRows?.asList()).containsExactly(2)
    assertThat(selection?.view?.qualifiedName).isEqualTo(FQCN_TEXT_VIEW)
    assertThat(selection?.view?.viewId?.name).isEqualTo("title")
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate another down arrow keyboard event in the search field: the next match should be found (back to DecorView)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.tree?.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree?.selectionRows?.asList()).containsExactly(0)
    assertThat(selection?.view?.qualifiedName).isEqualTo(DECOR_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate an up arrow keyboard event in the search field: the previous match should be found (TextView)
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP)
    selection = tree.tree?.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree?.selectionRows?.asList()).containsExactly(2)
    assertThat(selection?.view?.qualifiedName).isEqualTo(FQCN_TEXT_VIEW)
    assertThat(selection?.view?.viewId?.name).isEqualTo("title")
    assertThat(model.selection).isSameAs(selection?.view)

    // Accepting a matched value, should close the search field
    val callbacks: ToolWindowCallback = mock()
    tree.registerCallbacks(callbacks)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    verify(callbacks).stopFiltering()
  }

  @RunsInEdt
  @Test
  fun keyTypedStartsFiltering() {
    TreeSettings.hideSystemNodes = false
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    setToolContext(tree, inspector)
    UIUtil.dispatchAllInvocationEvents()

    val callbacks: ToolWindowCallback = mock()
    tree.registerCallbacks(callbacks)
    val ui = FakeUi(tree.tree!!)
    ui.keyboard.setFocus(tree.tree!!)
    ui.keyboard.type('T'.toInt())
    verify(callbacks).startFiltering("T")
  }

  @RunsInEdt
  @Test
  fun testSystemNodeWithMultipleChildren() {
    TreeSettings.hideSystemNodes = true
    TreeSettings.composeAsCallstack = true

    val launcher: InspectorClientLauncher = mock()
    val model = InspectorModel(projectRule.project)
    val inspector = LayoutInspector(launcher, model, MoreExecutors.directExecutor())
    val treePanel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    setToolContext(treePanel, inspector)
    val window = window(ROOT, ROOT) {
      compose(2, "App", composePackageHash = USER_PKG) {
        compose(3, "Column", composePackageHash = USER_PKG) {
          compose(4, "Layout", composePackageHash = SYSTEM_PKG) {
            compose(5, "Text", composePackageHash = USER_PKG)
            compose(6, "Box", composePackageHash = USER_PKG)
            compose(7, "Button", composePackageHash = USER_PKG)
          }
        }
      }
    }
    model.update(window, listOf(ROOT), 1)

    // Verify that "Layout" is omitted from the component tree, and that "Column" has 3 children:
    val root = treePanel.tree?.model?.root as TreeViewNode
    val expected =
      compose(2, "App", composePackageHash = USER_PKG) {
        compose(3, "Column", composePackageHash = USER_PKG) {
          compose(5, "Text", composePackageHash = USER_PKG)
          compose(6, "Box", composePackageHash = USER_PKG)
          compose(7, "Button", composePackageHash = USER_PKG)
        }
      }.build()
    assertTreeStructure(root.children.single(), expected)
  }

  @RunsInEdt
  @Test
  fun testSemanticTrees() {
    TreeSettings.hideSystemNodes = false

    val launcher: InspectorClientLauncher = mock()
    val model = InspectorModel(projectRule.project)
    val inspector = LayoutInspector(launcher, model, MoreExecutors.directExecutor())
    val treePanel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    setToolContext(treePanel, inspector)
    val window = window(ROOT, ROOT) {
      compose(2, "App") {
        compose(3, "Text", composeFlags = FLAG_HAS_MERGED_SEMANTICS or FLAG_HAS_UNMERGED_SEMANTICS)
        compose(4, "Column", composeFlags = FLAG_HAS_MERGED_SEMANTICS) {
          compose(5, "Layout", composeFlags = FLAG_SYSTEM_DEFINED) {
            compose(6, "Text", composeFlags = FLAG_HAS_UNMERGED_SEMANTICS)
            compose(7, "Box", composeFlags = FLAG_HAS_UNMERGED_SEMANTICS)
            compose(8, "Button", composeFlags = FLAG_HAS_UNMERGED_SEMANTICS)
          }
        }
      }
    }
    model.update(window, listOf(ROOT), 1)
    val root = treePanel.tree?.model?.root as TreeViewNode
    assertTreeStructure(root, expected =
    compose(-1, "root") {
      view(1, qualifiedName = CLASS_VIEW) {
        compose(2, "App") {
          compose(3, "Text")
          compose(4, "Column") {
            compose(5, "Layout") {
              compose(6, "Text")
              compose(7, "Box")
              compose(8, "Button")
            }
          }
        }
      }
    }.build())

    TreeSettings.mergedSemanticsTree = true
    treePanel.refresh()

    assertTreeStructure(root, expected =
    compose(-1, "root") {
      compose(3, "Text")
      compose(4, "Column")
    }.build())

    TreeSettings.mergedSemanticsTree = false
    TreeSettings.unmergedSemanticsTree = true
    treePanel.refresh()

    assertTreeStructure(root, expected =
    compose(-1, "root") {
      compose(3, "Text")
      compose(6, "Text")
      compose(7, "Box")
      compose(8, "Button")
    }.build())

    TreeSettings.mergedSemanticsTree = true
    TreeSettings.unmergedSemanticsTree = true
    treePanel.refresh()

    assertTreeStructure(root, expected =
    compose(-1, "root") {
      compose(3, "Text")
      compose(4, "Column") {
        compose(6, "Text")
        compose(7, "Box")
        compose(8, "Button")
      }
    }.build())
  }

  private fun setToolContext(tree: LayoutInspectorTreePanel, inspector: LayoutInspector) {
    tree.setToolContext(inspector)
    // Normally the tree would have received structural changes when the mode was loaded.
    // Mimic that here:
    val model = inspector.layoutInspectorModel
    model.windows.values.forEach { window -> model.modificationListeners.forEach { it(window, window, true) } }
  }

  private fun assertTreeStructure(node: TreeViewNode, expected: ViewNode) {
    val current = node.view
    assertThat(current.drawId).isEqualTo(expected.drawId)
    assertThat(current.qualifiedName).isEqualTo(expected.qualifiedName)
    assertThat(node.children.size).isEqualTo(expected.children.size)
    for (index in node.children.indices) {
      assertTreeStructure(node.children[index], expected.children[index])
    }
  }
}
