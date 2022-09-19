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

import com.android.SdkConstants
import com.android.SdkConstants.FQCN_RELATIVE_LAYOUT
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.flags.junit.SetFlagRule
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.Density
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.adtui.swing.laf.HeadlessTreeUI
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.adtui.workbench.ToolWindowCallback
import com.android.tools.componenttree.treetable.TreeTableModelImplAdapter
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.compose
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.FLAG_HAS_MERGED_SEMANTICS
import com.android.tools.idea.layoutinspector.model.FLAG_HAS_UNMERGED_SEMANTICS
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
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableRoot
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.Root
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewResource
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.sendEvent
import com.android.tools.idea.layoutinspector.util.DECOR_VIEW
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.util.FileOpenCaptureRule
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.UpdateSettingsCommand
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.verify
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreeModel

private const val SYSTEM_PKG = -1
private const val USER_PKG = 123
private const val TIMEOUT = 20L
private val TIMEOUT_UNIT = TimeUnit.SECONDS

private val PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class LayoutInspectorTreePanelTreeTest : LayoutInspectorTreePanelTest(useTreeTable = false)

class LayoutInspectorTreePanelTreeTableTest : LayoutInspectorTreePanelTest(useTreeTable = true)

abstract class LayoutInspectorTreePanelTest(useTreeTable: Boolean) {
  private val disposableRule = DisposableRule()
  private val projectRule = AndroidProjectRule.withSdk()
  private val appInspectorRule = AppInspectionInspectorRule(disposableRule.disposable)
  private val inspectorRule = LayoutInspectorRule(listOf(appInspectorRule.createInspectorClientProvider()), projectRule) {
    it.name == PROCESS.name
  }
  private val treeRule = SetFlagRule(StudioFlags.USE_COMPONENT_TREE_TABLE, useTreeTable)
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)
  private var lastUpdateSettingsCommand: UpdateSettingsCommand? = null
  private var updateSettingsCommands = 0
  private var updateSettingsLatch: ReportingCountDownLatch? = null

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule)
    .around(appInspectorRule)
    .around(inspectorRule)
    .around(fileOpenCaptureRule)
    .around(treeRule)
    .around(EdtRule())
    .around(disposableRule)!!

  @Before
  fun setUp() {
    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())

    projectRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/resource").toString()
    projectRule.fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML)
    projectRule.fixture.copyFileToProject("res/layout/demo.xml")

    inspectorRule.inspector.treeSettings.showRecompositions = true

    appInspectorRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      appInspectorRule.viewInspector.connection.sendEvent {
        rootsEventBuilder.apply {
          addIds(1L)
        }
      }

      appInspectorRule.viewInspector.connection.sendEvent {
        layoutEventBuilder.apply {
          ViewString(1, "com.android.internal.policy")
          ViewString(2, "DecorView")
          ViewString(3, "demo")
          ViewString(4, "layout")
          ViewString(5, "android.widget")
          ViewString(6, "RelativeLayout")
          ViewString(7, "TextView")
          ViewString(8, "id")
          ViewString(9, "title")
          ViewString(10, "android")
          ViewString(11, "AppTheme")
          ViewString(12, "http://schemas.android.com/apk/res/myapp")
          ViewString(13, "style")

          Root {
            id = ROOT
            packageName = 1
            className = 2
            ViewNode {
              id = VIEW1
              packageName = 5
              className = 6
              layoutResource = ViewResource(4, 12, 3)
              ViewNode {
                id = VIEW2
                packageName = 5
                className = 7
                resource = ViewResource(8, 10, 9)
                layoutResource = ViewResource(4, 12, 3)
              }
              ViewNode {
                id = VIEW3
                packageName = 5
                className = 6
                layoutResource = ViewResource(4, 12, 3)
                ViewNode {
                  id = VIEW4
                  packageName = 5
                  className = 7
                  layoutResource = ViewResource(4, 12, 3)
                }
              }
            }
          }

          appContextBuilder.apply {
            theme = ViewResource(13, 12, 11)
          }
          configurationBuilder.apply {
            density = Density.DEFAULT_DENSITY
            fontScale = 1.0f
          }
        }
      }

      LayoutInspectorViewProtocol.Response.newBuilder().setStartFetchResponse(
        LayoutInspectorViewProtocol.StartFetchResponse.getDefaultInstance()).build()
    }

    appInspectorRule.composeInspector.interceptWhen({ it.hasGetComposablesCommand() }) { _ ->
      LayoutInspectorComposeProtocol.Response.newBuilder().apply {
        getComposablesResponseBuilder.apply {
          ComposableString(1, "com.example")
          ComposableString(2, "File1.kt")
          ComposableString(3, "Button")
          ComposableString(3, "Text")

          ComposableRoot {
            viewId = VIEW4
            ComposableNode {
              id = COMPOSE1
              packageHash = 1
              filename = 2
              name = 3
              recomposeCount = 7
              recomposeSkips = 14
              ComposableNode {
                id = COMPOSE2
                packageHash = 1
                filename = 2
                name = 4
                recomposeCount = 9
                recomposeSkips = 33
              }
            }
          }
        }
      }.build()
    }

    appInspectorRule.composeInspector.interceptWhen({ it.hasUpdateSettingsCommand() }) { command ->
      lastUpdateSettingsCommand = command.updateSettingsCommand
      updateSettingsCommands++
      updateSettingsLatch?.countDown()
      LayoutInspectorComposeProtocol.Response.newBuilder().apply {
        updateSettingsResponse = LayoutInspectorComposeProtocol.UpdateSettingsResponse.getDefaultInstance()
      }.build()
    }

    updateSettingsLatch = ReportingCountDownLatch(1)
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(PROCESS)
    inspectorRule.processes.selectedProcess = PROCESS
    waitForCondition(TIMEOUT, TIMEOUT_UNIT) { inspectorRule.inspectorModel.windows.isNotEmpty() }
    updateSettingsLatch?.await(TIMEOUT, TIMEOUT_UNIT)
    updateSettingsLatch = null
  }

  @Test
  fun testGotoDeclaration() {
    runInEdtAndWait {
      val disposable = projectRule.fixture.testRootDisposable
      val tree = LayoutInspectorTreePanel(disposable)
      val model = inspectorRule.inspectorModel
      val inspector = inspectorRule.inspector
      setToolContext(tree, inspector)

      model.setSelection(model["title"], SelectionOrigin.INTERNAL)

      val focusManager = FakeKeyboardFocusManager(disposable)
      focusManager.focusOwner = tree.component

      val dispatcher = IdeKeyEventDispatcher(null)
      val modifier = if (SystemInfo.isMac) KeyEvent.META_DOWN_MASK else KeyEvent.CTRL_DOWN_MASK
      dispatcher.dispatchKeyEvent(KeyEvent(tree.component, KeyEvent.KEY_PRESSED, 0, modifier, KeyEvent.VK_B, 'B'))
    }

    fileOpenCaptureRule.checkEditor("demo.xml", 9, "<TextView")
  }

  @RunsInEdt
  @Test
  fun testGotoDeclarationByDoubleClick() {
    val panel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    setToolContext(panel, inspector)
    UIUtil.dispatchAllInvocationEvents()

    val focusComponent = panel.focusComponent
    val tree = panel.tree
    (focusComponent as? JTable)?.setUI(HeadlessTableUI())
    tree.setUI(HeadlessTreeUI())
    focusComponent.setBounds(0, 0, 500, 1000)
    TreeUtil.expandAll(tree)
    val bounds = tree.getRowBounds(1)
    val ui = FakeUi(focusComponent)
    ui.mouse.doubleClick(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)

    fileOpenCaptureRule.checkEditor("demo.xml", 9, "<TextView")

    val data = DynamicLayoutInspectorSession.newBuilder()
    inspector.currentClient.stats.save(data)
    assertThat(data.gotoDeclaration.doubleClicks).isEqualTo(1)
  }

  @RunsInEdt
  @Test
  fun testMultiWindowWithVisibleSystemNodes() {
    val demo = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "demo")
    val panel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val model = inspectorRule.inspectorModel
    val inspector = inspectorRule.inspector
    inspector.treeSettings.hideSystemNodes = false
    setToolContext(panel, inspector)
    val tree = panel.tree
    UIUtil.dispatchAllInvocationEvents()
    assertThat(tree.rowCount).isEqualTo(1)
    assertThat(tree.getPathForRow(0).lastPathComponent).isEqualTo(model[ROOT]!!.treeNode)
    assertThat(model[ROOT]!!.qualifiedName).isEqualTo(DECOR_VIEW)

    model.update(window(ROOT, ROOT) { view(VIEW4) { view(VIEW1, layout = demo) } }, listOf(ROOT), 0)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(tree).blockingGet(1, TimeUnit.SECONDS)
    assertThat(tree.rowCount).isEqualTo(3)
    assertThat(tree.getPathForRow(0).lastPathComponent).isEqualTo(model[ROOT]!!.treeNode)
    assertThat(tree.getPathForRow(1).lastPathComponent).isEqualTo(model[VIEW4]!!.treeNode)
    assertThat(tree.getPathForRow(2).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)

    model.update(window(VIEW2, VIEW2) { view(VIEW3) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(tree).blockingGet(1, TimeUnit.SECONDS)
    assertThat(tree.rowCount).isEqualTo(5)
    assertThat(tree.getPathForRow(0).lastPathComponent).isEqualTo(model[ROOT]!!.treeNode)
    assertThat(tree.getPathForRow(1).lastPathComponent).isEqualTo(model[VIEW4]!!.treeNode)
    assertThat(tree.getPathForRow(2).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)
    assertThat(tree.getPathForRow(3).lastPathComponent).isEqualTo(model[VIEW2]!!.treeNode)
    assertThat(tree.getPathForRow(4).lastPathComponent).isEqualTo(model[VIEW3]!!.treeNode)

    model.update(window(VIEW2, VIEW2, layoutFlags = WINDOW_MANAGER_FLAG_DIM_BEHIND) { view(VIEW3) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    // Still 5: the dimmer is drawn but isn't in the tree
    assertThat(tree.rowCount).isEqualTo(5)
  }

  @RunsInEdt
  @Test
  fun testMultiWindowWithHiddenSystemNodes() {
    val android = ResourceReference(ResourceNamespace.ANDROID, ResourceType.LAYOUT, "simple_screen")
    val demo = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "demo")
    val panel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val model = inspectorRule.inspectorModel
    val inspector = inspectorRule.inspector
    inspector.treeSettings.hideSystemNodes = true
    setToolContext(panel, inspector)
    val tree = panel.tree
    UIUtil.dispatchAllInvocationEvents()
    assertThat(tree.rowCount).isEqualTo(1)
    assertThat(tree.getPathForRow(0).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)
    assertThat(model[VIEW1]!!.qualifiedName).isEqualTo(FQCN_RELATIVE_LAYOUT)

    // ROOT & VIEW4 are system views (no layout, android layout)
    model.update(
      window(ROOT, ROOT) {
        view(VIEW4, layout = android) {
          view(VIEW1, layout = demo)
        }
      }, listOf(ROOT), 0)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(tree).blockingGet(1, TimeUnit.SECONDS)
    assertThat(tree.rowCount).isEqualTo(1)
    assertThat(tree.getPathForRow(0).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)

    model.update(window(VIEW2, VIEW2) { view(VIEW3, layout = demo) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(tree).blockingGet(1, TimeUnit.SECONDS)
    assertThat(tree.rowCount).isEqualTo(2)
    assertThat(tree.getPathForRow(0).lastPathComponent).isEqualTo(model[VIEW1]!!.treeNode)
    assertThat(tree.getPathForRow(1).lastPathComponent).isEqualTo(model[VIEW3]!!.treeNode)

    model.update(window(VIEW2, VIEW2, layoutFlags = WINDOW_MANAGER_FLAG_DIM_BEHIND) { view(VIEW3, layout = demo) }, listOf(ROOT, VIEW2), 0)
    UIUtil.dispatchAllInvocationEvents()
    // Still 2: the dimmer is drawn but isn't in the tree
    assertThat(tree.rowCount).isEqualTo(2)
  }

  @RunsInEdt
  @Test
  fun testSelectFromComponentTree() {
    val panel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val model = inspectorRule.inspectorModel
    val inspector = inspectorRule.inspector
    inspector.treeSettings.hideSystemNodes = false
    setToolContext(panel, inspector)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(panel.tree).blockingGet(10, TimeUnit.SECONDS)

    var selectedView: ViewNode? = null
    var selectionOrigin = SelectionOrigin.INTERNAL
    model.selectionListeners.add { _, newSelection, origin ->
      selectedView = newSelection
      selectionOrigin = origin
    }

    val table = panel.focusComponent as? JTable
    if (table != null) {
      table.addRowSelectionInterval(2, 2)
    }
    else {
      panel.tree.setSelectionRow(2)
    }
    assertThat(selectionOrigin).isEqualTo(SelectionOrigin.COMPONENT_TREE)
    assertThat(selectedView?.drawId).isEqualTo(VIEW2)
    assertThat(selectedView?.qualifiedName).isEqualTo(FQCN_TEXT_VIEW)
  }

  @RunsInEdt
  @Test
  fun testFiltering() {
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    val model = inspectorRule.inspectorModel
    inspector.treeSettings.hideSystemNodes = false
    setToolContext(tree, inspector)
    UIUtil.dispatchAllInvocationEvents()

    // The component tree is now collapsed with the top element: DecorView
    // Demo has 5 views: DecorView(ROOT), RelativeLayout(VIEW1), TextView(VIEW2), RelativeLayout(VIEW3), TextView(VIEW4).
    // We should be able to find views even if they are currently collapsed:
    tree.setFilter("View")
    UIUtil.dispatchAllInvocationEvents()
    var selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(0)
    assertThat(selection?.view?.qualifiedName).isEqualTo(DECOR_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(selection?.view?.drawId).isEqualTo(ROOT)
    assertThat(model.selection).isSameAs(selection?.view)

    // Remove the current selection and
    // Simulate a down arrow keyboard event in the search field: the first match should be found again (DecorView)
    model.setSelection(null, SelectionOrigin.COMPONENT_TREE)
    val searchField = JPanel()
    searchField.addKeyListener(tree.filterKeyListener)
    val ui = FakeUi(searchField)
    ui.keyboard.setFocus(searchField)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(0)
    assertThat(selection?.view?.qualifiedName).isEqualTo(DECOR_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(selection?.view?.drawId).isEqualTo(ROOT)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate a down arrow keyboard event in the search field: the next match should be found (TextView(VIEW2))
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(2)
    assertThat(selection?.view?.qualifiedName).isEqualTo(FQCN_TEXT_VIEW)
    assertThat(selection?.view?.viewId?.name).isEqualTo("title")
    assertThat(selection?.view?.drawId).isEqualTo(VIEW2)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate another down arrow keyboard event in the search field: the next match should be found (TextView(VIEW4))
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(4)
    assertThat(selection?.view?.qualifiedName).isEqualTo(FQCN_TEXT_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(selection?.view?.drawId).isEqualTo(VIEW4)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate another down arrow keyboard event in the search field: the next match should be found (back to DecorView)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(0)
    assertThat(selection?.view?.qualifiedName).isEqualTo(DECOR_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(selection?.view?.drawId).isEqualTo(ROOT)
    assertThat(model.selection).isSameAs(selection?.view)

    // Now hide TextView(VIEW2)
    model.hideSubtree(model[VIEW2]!!)

    // Simulate another down arrow keyboard event in the search field: the next match should be found (TextView(VIEW4))
    // Because TextView(VIEW2) is skipped when hidden
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(4)
    assertThat(selection?.view?.qualifiedName).isEqualTo(FQCN_TEXT_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(selection?.view?.drawId).isEqualTo(VIEW4)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate another down arrow keyboard event in the search field: the next match should be found (back to DecorView)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(0)
    assertThat(selection?.view?.qualifiedName).isEqualTo(DECOR_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(selection?.view?.drawId).isEqualTo(ROOT)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate an up arrow keyboard event in the search field: the previous match should be found (TextView(VIEW4))
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP)
    selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(4)
    assertThat(selection?.view?.qualifiedName).isEqualTo(FQCN_TEXT_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(selection?.view?.drawId).isEqualTo(VIEW4)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate another up arrow keyboard event in the search field: the next match should be found (back to DecorView)
    // (skipping TextView(VIEW2))
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP)
    selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(0)
    assertThat(selection?.view?.qualifiedName).isEqualTo(DECOR_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(selection?.view?.drawId).isEqualTo(ROOT)
    assertThat(model.selection).isSameAs(selection?.view)

    // Remove the current selection and
    // Simulate an up arrow keyboard event in the search field: the first match should be found again (DecorView)
    model.setSelection(null, SelectionOrigin.COMPONENT_TREE)
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP)
    selection = tree.tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(tree.tree.selectionRows?.asList()).containsExactly(4)
    assertThat(selection?.view?.qualifiedName).isEqualTo(FQCN_TEXT_VIEW)
    assertThat(selection?.view?.viewId?.name).isNull()
    assertThat(selection?.view?.drawId).isEqualTo(VIEW4)
    assertThat(model.selection).isSameAs(selection?.view)

    // Accepting a matched value, should close the search field
    val callbacks: ToolWindowCallback = mock()
    tree.registerCallbacks(callbacks)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    verify(callbacks).stopFiltering()
  }

  @RunsInEdt
  @Test
  fun navigationAfterUpdateUI() {
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val jTree = tree.tree
    val inspector = inspectorRule.inspector
    inspector.treeSettings.hideSystemNodes = false
    setToolContext(tree, inspector)
    UIUtil.dispatchAllInvocationEvents()
    TreeUtil.promiseExpandAll(jTree).blockingGet(5, TimeUnit.SECONDS)
    jTree.addSelectionRow(0)
    assertThat(jTree.rowCount).isEqualTo(7)
    assertThat(jTree.leadSelectionRow).isEqualTo(0)

    val ui = FakeUi(jTree)
    ui.keyboard.setFocus(jTree)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertThat(jTree.leadSelectionRow).isEqualTo(1)

    // Imitate a theme change which requires a new UI, and setup of action maps:
    jTree.updateUI()
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertThat(jTree.leadSelectionRow).isEqualTo(2)
  }

  @RunsInEdt
  @Test
  fun keyTypedStartsFiltering() {
    val panel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    inspector.treeSettings.hideSystemNodes = false
    setToolContext(panel, inspector)
    UIUtil.dispatchAllInvocationEvents()

    val callbacks: ToolWindowCallback = mock()
    panel.registerCallbacks(callbacks)
    val ui = FakeUi(panel.focusComponent)
    ui.keyboard.setFocus(panel.focusComponent)
    ui.keyboard.type('T'.code)
    verify(callbacks).startFiltering("T")
  }

  @RunsInEdt
  @Test
  fun testTreeNavigationWithUpDownKeys() {
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    inspector.treeSettings.hideSystemNodes = false
    setToolContext(tree, inspector)
    UIUtil.dispatchAllInvocationEvents()

    TreeUtil.promiseExpand(tree.tree, 2).blockingGet(1, TimeUnit.SECONDS)
    assertThat(tree.tree.rowCount).isEqualTo(2)
    tree.tree.addSelectionRow(0)
    assertThat(tree.tree.selectionRows!!.asList()).containsExactly(0)
    val ui = FakeUi(tree.tree)
    ui.keyboard.setFocus(tree.tree)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertThat(tree.tree.selectionRows!!.asList()).containsExactly(1)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    assertThat(tree.tree.selectionRows!!.asList()).containsExactly(1)
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP)
    assertThat(tree.tree.selectionRows!!.asList()).containsExactly(0)
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP)
    assertThat(tree.tree.selectionRows!!.asList()).containsExactly(0)
  }

  @RunsInEdt
  @Test
  fun testSystemNodeWithMultipleChildren() {
    val launcher: InspectorClientLauncher = mock()
    val model = InspectorModel(projectRule.project)
    val inspector = LayoutInspector(launcher, model, FakeTreeSettings(), MoreExecutors.directExecutor ())
    val treePanel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    inspector.treeSettings.hideSystemNodes = true
    inspector.treeSettings.composeAsCallstack = true
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
    val root = treePanel.tree.model?.root as TreeViewNode
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
    val launcher: InspectorClientLauncher = mock()
    val model = InspectorModel(projectRule.project)
    val inspector = LayoutInspector(launcher, model, FakeTreeSettings(), MoreExecutors.directExecutor())
    val treePanel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val tree = treePanel.tree
    inspector.treeSettings.hideSystemNodes = false
    setToolContext(treePanel, inspector)
    val window = window(ROOT, ROOT) {
      compose(2, "App") {
        compose(3, "MaterialTheme")
        compose(4, "Text", composeFlags = FLAG_HAS_MERGED_SEMANTICS or FLAG_HAS_UNMERGED_SEMANTICS)
        compose(5, "Column", composeFlags = FLAG_HAS_MERGED_SEMANTICS) {
          compose(6, "Row") {
            compose(7, "Layout") {
              compose(8, "Text", composeFlags = FLAG_HAS_UNMERGED_SEMANTICS)
              compose(9, "Box")
              compose(10, "Button", composeFlags = FLAG_HAS_UNMERGED_SEMANTICS)
            }
          }
        }
      }
    }

    model.update(window, listOf(ROOT), 1)
    UIUtil.dispatchAllInvocationEvents()

    // Turn on highlightSemantics
    inspector.treeSettings.highlightSemantics = true
    treePanel.updateSemanticsFiltering()

    // Test de-emphasis
    val viewType = treePanel.nodeViewType
    assertThat(viewType.isDeEmphasized(model[2]!!.treeNode)).isTrue()
    assertThat(viewType.isDeEmphasized(model[3]!!.treeNode)).isTrue()
    assertThat(viewType.isDeEmphasized(model[4]!!.treeNode)).isFalse()
    assertThat(viewType.isDeEmphasized(model[5]!!.treeNode)).isFalse()
    assertThat(viewType.isDeEmphasized(model[6]!!.treeNode)).isTrue()
    assertThat(viewType.isDeEmphasized(model[7]!!.treeNode)).isTrue()
    assertThat(viewType.isDeEmphasized(model[8]!!.treeNode)).isFalse()
    assertThat(viewType.isDeEmphasized(model[9]!!.treeNode)).isTrue()
    assertThat(viewType.isDeEmphasized(model[10]!!.treeNode)).isFalse()

    // The first node with semantics should have been selected
    var selection = tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(selection?.view?.qualifiedName).isEqualTo("Text")
    assertThat(selection?.view?.drawId).isEqualTo(4)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate a down arrow keyboard event in the tree: the next node with semantic information should be selected
    val ui = FakeUi(treePanel.focusComponent)
    ui.keyboard.setFocus(treePanel.focusComponent)
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(selection?.view?.qualifiedName).isEqualTo("Column")
    assertThat(selection?.view?.drawId).isEqualTo(5)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate another down arrow keyboard event in the tree: expect to skip Row and Layout since these don't have semantic information
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(selection?.view?.qualifiedName).isEqualTo("Text")
    assertThat(selection?.view?.drawId).isEqualTo(8)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate another down arrow keyboard event in the tree: expect to skip Box since it doesn't have semantic information
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(selection?.view?.qualifiedName).isEqualTo("Button")
    assertThat(selection?.view?.drawId).isEqualTo(10)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate another down arrow keyboard event in the tree: wrap and find the first node with semantics
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN)
    selection = tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(selection?.view?.qualifiedName).isEqualTo("Text")
    assertThat(selection?.view?.drawId).isEqualTo(4)
    assertThat(model.selection).isSameAs(selection?.view)

    // Simulate an up arrow keyboard event in the tree: wrap and find the last node with semantics
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP)
    selection = tree.lastSelectedPathComponent as? TreeViewNode
    assertThat(selection?.view?.qualifiedName).isEqualTo("Button")
    assertThat(selection?.view?.drawId).isEqualTo(10)
    assertThat(model.selection).isSameAs(selection?.view)
  }

  @Test
  fun testNonStructuralModelChanges() {
    if (!StudioFlags.USE_COMPONENT_TREE_TABLE.get()) {
      // This test is specific to the TreeTable implementation of the component tree
      return
    }
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    tree.setToolContext(inspector)
    runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
    assertThat((tree.focusComponent as JTable).columnModel.getColumn(1).maxWidth).isGreaterThan(0)
    val treeModel = tree.componentTreeModel as TreeModel
    var columnDataChangeCount = 0
    var treeChangeCount = 0
    treeModel.addTreeModelListener(object : TreeTableModelImplAdapter() {
      override fun columnDataChanged() {
        columnDataChangeCount++
      }

      override fun treeChanged(event: TreeModelEvent) {
        treeChangeCount++
      }
    })
    val model = inspector.layoutInspectorModel
    model.windows.values.forEach { window -> model.modificationListeners.forEach { it(window, window, false) } }
    assertThat(columnDataChangeCount).isEqualTo(1)
    assertThat(treeChangeCount).isEqualTo(0)
  }

  @RunsInEdt
  @Test
  fun testRecompositionColumnVisibility() {
    if (!StudioFlags.USE_COMPONENT_TREE_TABLE.get()) {
      // This test is specific to the TreeTable implementation of the component tree
      return
    }
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    inspector.treeSettings.showRecompositions = true
    tree.setToolContext(inspector)
    val columnModel = (tree.focusComponent as JTable).columnModel
    assertThat(columnModel.getColumn(1).maxWidth).isGreaterThan(0)
    assertThat(columnModel.getColumn(2).maxWidth).isGreaterThan(0)

    inspector.treeSettings.showRecompositions = false
    tree.updateRecompositionColumnVisibility()
    UIUtil.dispatchAllInvocationEvents()
    assertThat(columnModel.getColumn(1).maxWidth).isEqualTo(0)
    assertThat(columnModel.getColumn(2).maxWidth).isEqualTo(0)

    inspector.treeSettings.showRecompositions = true
    tree.updateRecompositionColumnVisibility()
    UIUtil.dispatchAllInvocationEvents()
    assertThat(columnModel.getColumn(1).maxWidth).isGreaterThan(0)
    assertThat(columnModel.getColumn(2).maxWidth).isGreaterThan(0)

    // The recomposition columns should be hidden when disconnected:
    inspectorRule.launcher.disconnectActiveClient(10, TimeUnit.SECONDS)
    tree.updateRecompositionColumnVisibility()
    UIUtil.dispatchAllInvocationEvents()
    assertThat(columnModel.getColumn(1).maxWidth).isEqualTo(0)
    assertThat(columnModel.getColumn(2).maxWidth).isEqualTo(0)
  }

  @Test
  fun testResetRecompositionCounts() {
    if (!StudioFlags.USE_COMPONENT_TREE_TABLE.get()) {
      // This test is specific to the TreeTable implementation of the component tree
      return
    }
    val tree = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    val model = inspector.layoutInspectorModel
    val compose1 = model[COMPOSE1] as ComposeViewNode
    val compose2 = model[COMPOSE2] as ComposeViewNode
    var selectionUpdate = 0
    model.selectionListeners.add { _, _, _ ->
      selectionUpdate++
    }

    assertThat(compose1.recompositions.count).isEqualTo(7)
    assertThat(compose1.recompositions.skips).isEqualTo(14)
    assertThat(compose2.recompositions.count).isEqualTo(9)
    assertThat(compose2.recompositions.skips).isEqualTo(33)

    setToolContext(tree, inspector)
    UIUtil.pump()
    val table = tree.focusComponent as JTable
    val scrollPane = tree.component as JBScrollPane
    scrollPane.size = Dimension(800, 1000)
    val ui = FakeUi(scrollPane, createFakeWindow = true)
    val treeColumnWidth = table.columnModel.getColumn(0).width
    updateSettingsLatch = ReportingCountDownLatch(1)

    // Click on the reset recomposition counts button in the table header:
    ui.mouse.click(treeColumnWidth - 8, 8)

    updateSettingsLatch?.await(TIMEOUT, TIMEOUT_UNIT)

    assertThat(compose1.recompositions.count).isEqualTo(0)
    assertThat(compose1.recompositions.skips).isEqualTo(0)
    assertThat(compose2.recompositions.count).isEqualTo(0)
    assertThat(compose2.recompositions.skips).isEqualTo(0)
    assertThat(selectionUpdate).isEqualTo(1)
    assertThat(updateSettingsCommands).isEqualTo(2)
    assertThat(lastUpdateSettingsCommand?.includeRecomposeCounts).isTrue()

    val data = DynamicLayoutInspectorSession.newBuilder()
    inspector.currentClient.stats.save(data)
    assertThat(data.compose.recompositionResetClicks).isEqualTo(1)
  }

  @Test
  fun testRecompositionCountsAreLogged() {
    val panel = LayoutInspectorTreePanel(projectRule.fixture.testRootDisposable)
    val inspector = inspectorRule.inspector
    setToolContext(panel, inspector)

    val data = DynamicLayoutInspectorSession.newBuilder()
    inspector.currentClient.stats.save(data)
    assertThat(data.compose.maxRecompositionCount).isEqualTo(9)
    assertThat(data.compose.maxRecompositionSkips).isEqualTo(33)
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
    ViewNode.readAccess {
      assertThat(node.children.size).isEqualTo(expected.children.size)
      for (index in node.children.indices) {
        assertTreeStructure(node.children[index], expected.children[index])
      }
    }
  }
}
