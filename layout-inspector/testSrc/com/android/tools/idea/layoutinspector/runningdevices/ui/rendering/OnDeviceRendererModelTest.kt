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
package com.android.tools.idea.layoutinspector.runningdevices.ui.rendering

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.COMPOSE3
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.ui.BASE_COLOR_ARGB
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.ui.HOVER_COLOR_ARGB
import com.android.tools.idea.layoutinspector.ui.RenderSettings
import com.android.tools.idea.layoutinspector.ui.SELECTION_COLOR_ARGB
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RECOMPOSITION_COLOR_RED_ARGB
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.viewWindow
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import java.awt.Rectangle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnDeviceRendererModelTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()

  private lateinit var inspectorModel: InspectorModel
  private lateinit var onDeviceRendererModel: OnDeviceRendererModel
  private lateinit var treeSettings: FakeTreeSettings
  private lateinit var renderSettings: RenderSettings

  @Before
  fun setUp() {
    inspectorModel =
      model(disposableRule.disposable) {
        view(ROOT, 0, 0, 100, 100) {
          view(VIEW1, 10, 15, 25, 25) {}
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
        }
      }

    treeSettings = FakeTreeSettings()
    renderSettings = FakeRenderSettings()

    onDeviceRendererModel =
      OnDeviceRendererModel(
        parentDisposable = disposableRule.disposable,
        inspectorModel = inspectorModel,
        treeSettings = treeSettings,
        renderSettings = renderSettings,
      )
  }

  @Test
  fun testFindNodesAt() = runTest {
    val nodes1 = onDeviceRendererModel.findNodesAt(15.0, 55.0, ROOT)
    assertThat(nodes1).containsExactly(inspectorModel[COMPOSE1], inspectorModel[ROOT])

    val nodes2 = onDeviceRendererModel.findNodesAt(0.0, 0.0, ROOT)
    assertThat(nodes2).containsExactly(inspectorModel[ROOT])
  }

  @Test
  fun testSelectedNode() = runTest {
    onDeviceRendererModel.selectNode(15.0, 55.0, ROOT)
    testScheduler.advanceUntilIdle()

    val expectedInstructions =
      DrawInstruction(
        rootViewId = ROOT,
        bounds = Rectangle(10, 50, 80, 50),
        color = SELECTION_COLOR_ARGB,
      )
    val instructions1 = onDeviceRendererModel.selectedNode.first()
    assertThat(instructions1).isEqualTo(expectedInstructions)

    onDeviceRendererModel.selectNode(-1.0, -1.0, ROOT)
    testScheduler.advanceUntilIdle()

    val instructions2 = onDeviceRendererModel.selectedNode.first()
    assertThat(instructions2).isNull()
  }

  @Test
  fun testHoveredNode() = runTest {
    onDeviceRendererModel.hoverNode(15.0, 55.0, ROOT)
    testScheduler.advanceUntilIdle()

    val expectedInstructions =
      DrawInstruction(
        rootViewId = ROOT,
        bounds = Rectangle(10, 50, 80, 50),
        color = HOVER_COLOR_ARGB,
      )
    val instructions1 = onDeviceRendererModel.hoveredNode.first()
    assertThat(instructions1).isEqualTo(expectedInstructions)

    onDeviceRendererModel.hoverNode(-1.0, -1.0, ROOT)
    testScheduler.advanceUntilIdle()

    val instructions2 = onDeviceRendererModel.hoveredNode.first()
    assertThat(instructions2).isNull()
  }

  @Test
  fun testVisibleNodesChangeOnModelUpdates() = runTest {
    testScheduler.advanceUntilIdle()

    val expectedInstructions1 =
      listOf(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[VIEW1]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[COMPOSE1]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[ROOT]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
      )

    val instructions1 = onDeviceRendererModel.visibleNodes.first()
    assertThat(instructions1).isEqualTo(expectedInstructions1)

    val xrWindow = viewWindow(ROOT, 0, 0, 100, 200, isXr = true) { view(VIEW1, 25, 30, 50, 50) {} }
    inspectorModel.update(xrWindow, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()

    val expectedInstructions2 =
      listOf(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(25, 30, 50, 50),
          color = BASE_COLOR_ARGB,
        )
      )
    val instructions2 = onDeviceRendererModel.visibleNodes.first()
    assertThat(instructions2).isEqualTo(expectedInstructions2)
  }

  @Test
  fun testRecomposingNodesChangeOnModelUpdates() = runTest {
    testScheduler.advanceUntilIdle()

    val instructions1 = onDeviceRendererModel.recomposingNodes.first()
    assertThat(instructions1).isEqualTo(emptyList<DrawInstruction>())

    treeSettings.showRecompositions = true

    val newWindow =
      viewWindow(ROOT, 0, 0, 100, 200) {
        compose(COMPOSE2, name = "compose-node", x = 0, y = 0, width = 50, height = 50) {}
      }
    var composeNode2 = newWindow.root.flattenedList().find { it.drawId == COMPOSE2 }!!
    composeNode2.recompositions.highlightCount = 100f
    inspectorModel.update(newWindow, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()

    val expectedInstructions2 =
      listOf(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(0, 0, 50, 50),
          color = RECOMPOSITION_COLOR_RED_ARGB,
        )
      )
    val instructions2 = onDeviceRendererModel.recomposingNodes.first()
    assertThat(instructions2).isEqualTo(expectedInstructions2)

    // Disable recomposition counts
    inspectorModel.resetRecompositionCounts()
    treeSettings.showRecompositions = false

    val newWindow2 =
      viewWindow(ROOT, 0, 0, 100, 200) {
        compose(COMPOSE3, name = "compose-node", x = 0, y = 0, width = 20, height = 20) {}
      }
    var composeNode3 = newWindow2.root.flattenedList().find { it.drawId == COMPOSE3 }!!
    composeNode3.recompositions.highlightCount = 100f
    inspectorModel.update(newWindow2, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()

    val instructions3 = onDeviceRendererModel.recomposingNodes.first()
    assertThat(instructions3).isEqualTo(emptyList<DrawInstruction>())
  }

  @Test
  fun testModelUpdatesUpdateSelectedAndHoveredNodes() = runTest {
    val xrWindow1 = viewWindow(ROOT, 0, 0, 100, 200, isXr = true) { view(VIEW1, 25, 30, 50, 50) {} }
    inspectorModel.update(xrWindow1, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()

    val instructions1 = onDeviceRendererModel.visibleNodes.first()
    assertThat(instructions1)
      .isEqualTo(
        listOf(
          DrawInstruction(
            rootViewId = ROOT,
            bounds = Rectangle(25, 30, 50, 50),
            color = BASE_COLOR_ARGB,
          )
        )
      )

    onDeviceRendererModel.selectNode(x = 30.0, y = 35.0, rootId = ROOT)
    onDeviceRendererModel.hoverNode(x = 30.0, y = 35.0, rootId = ROOT)
    testScheduler.advanceUntilIdle()

    val instructions2 = onDeviceRendererModel.selectedNode.first()
    assertThat(instructions2)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(25, 30, 50, 50),
          color = SELECTION_COLOR_ARGB,
        )
      )
    val instructions3 = onDeviceRendererModel.hoveredNode.first()
    assertThat(instructions3)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(25, 30, 50, 50),
          color = HOVER_COLOR_ARGB,
        )
      )

    // Offset the selected view by 5.
    val xrWindow2 = viewWindow(ROOT, 0, 0, 100, 200, isXr = true) { view(VIEW1, 30, 35, 55, 55) {} }
    inspectorModel.update(xrWindow2, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()

    val instructions4 = onDeviceRendererModel.visibleNodes.first()
    assertThat(instructions4)
      .isEqualTo(
        listOf(
          DrawInstruction(
            rootViewId = ROOT,
            bounds = Rectangle(30, 35, 55, 55),
            color = BASE_COLOR_ARGB,
          )
        )
      )

    val instructions5 = onDeviceRendererModel.selectedNode.first()
    assertThat(instructions5)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(30, 35, 55, 55),
          color = SELECTION_COLOR_ARGB,
        )
      )
    val instructions6 = onDeviceRendererModel.hoveredNode.first()
    assertThat(instructions6)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(30, 35, 55, 55),
          color = HOVER_COLOR_ARGB,
        )
      )
  }

  @Test
  fun testInterceptClicks() = runTest {
    val interceptClicks1 = onDeviceRendererModel.interceptClicks.first()
    assertThat(interceptClicks1).isFalse()

    onDeviceRendererModel.setInterceptClicks(true)
    testScheduler.advanceUntilIdle()

    val interceptClicks2 = onDeviceRendererModel.interceptClicks.first()
    assertThat(interceptClicks2).isTrue()

    onDeviceRendererModel.setInterceptClicks(false)
    testScheduler.advanceUntilIdle()

    val interceptClicks3 = onDeviceRendererModel.interceptClicks.first()
    assertThat(interceptClicks3).isFalse()
  }

  @Test
  fun testDisposeRemovesListeners() = runTest {
    assertThat(inspectorModel.hoverListeners.size()).isEqualTo(1)
    assertThat(inspectorModel.modificationListeners.size()).isEqualTo(2)
    assertThat(inspectorModel.selectionListeners.size()).isEqualTo(1)

    Disposer.dispose(onDeviceRendererModel)

    assertThat(inspectorModel.hoverListeners.size()).isEqualTo(0)
    assertThat(inspectorModel.modificationListeners.size()).isEqualTo(1)
    assertThat(inspectorModel.selectionListeners.size()).isEqualTo(0)
  }

  @Test
  fun testNonVisibleNodesAreNotDrawn() = runTest {
    testScheduler.advanceUntilIdle()

    val expectedInstructions1 =
      listOf(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[VIEW1]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[COMPOSE1]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[ROOT]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
      )

    val instructions1 = onDeviceRendererModel.visibleNodes.first()
    assertThat(instructions1).isEqualTo(expectedInstructions1)

    inspectorModel.hideSubtree(inspectorModel[VIEW1]!!)
    testScheduler.advanceUntilIdle()

    val expectedInstructions2 =
      listOf(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[COMPOSE1]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[ROOT]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
      )

    val instructions2 = onDeviceRendererModel.visibleNodes.first()
    assertThat(instructions2).isEqualTo(expectedInstructions2)
  }

  @Test
  fun testNonVisibleNodesAreNotSelected() = runTest {
    onDeviceRendererModel.selectNode(15.0, 55.0, ROOT)
    testScheduler.advanceUntilIdle()

    val expectedInstructions1 =
      DrawInstruction(
        rootViewId = ROOT,
        bounds = Rectangle(10, 50, 80, 50),
        color = SELECTION_COLOR_ARGB,
      )
    val instructions1 = onDeviceRendererModel.selectedNode.first()
    assertThat(instructions1).isEqualTo(expectedInstructions1)

    onDeviceRendererModel.selectNode(-1.0, -1.0, ROOT)
    testScheduler.advanceUntilIdle()

    assertThat(inspectorModel.selection).isNull()

    inspectorModel.hideSubtree(inspectorModel[COMPOSE1]!!)
    testScheduler.advanceUntilIdle()

    // The previous node at (15, 55) is now hidden, clicking there selects a different node.
    onDeviceRendererModel.selectNode(15.0, 55.0, ROOT)
    testScheduler.advanceUntilIdle()

    val expectedInstructions2 =
      DrawInstruction(
        rootViewId = ROOT,
        bounds = Rectangle(0, 0, 100, 100),
        color = SELECTION_COLOR_ARGB,
      )
    val instructions2 = onDeviceRendererModel.selectedNode.first()
    assertThat(instructions2).isEqualTo(expectedInstructions2)
  }

  @Test
  fun testNonVisibleNodesAreNotHovered() = runTest {
    onDeviceRendererModel.hoverNode(15.0, 55.0, ROOT)
    testScheduler.advanceUntilIdle()

    val expectedInstructions1 =
      DrawInstruction(
        rootViewId = ROOT,
        bounds = Rectangle(10, 50, 80, 50),
        color = HOVER_COLOR_ARGB,
      )
    val instructions1 = onDeviceRendererModel.hoveredNode.first()
    assertThat(instructions1).isEqualTo(expectedInstructions1)

    onDeviceRendererModel.hoverNode(-1.0, -1.0, ROOT)
    testScheduler.advanceUntilIdle()

    assertThat(inspectorModel.hoveredNode).isNull()

    inspectorModel.hideSubtree(inspectorModel[COMPOSE1]!!)
    testScheduler.advanceUntilIdle()

    // The previous node at (15, 55) is now hidden, hovering there selects a different node.
    onDeviceRendererModel.hoverNode(15.0, 55.0, ROOT)
    testScheduler.advanceUntilIdle()

    val expectedInstructions2 =
      DrawInstruction(
        rootViewId = ROOT,
        bounds = Rectangle(0, 0, 100, 100),
        color = HOVER_COLOR_ARGB,
      )
    val instructions2 = onDeviceRendererModel.hoveredNode.first()
    assertThat(instructions2).isEqualTo(expectedInstructions2)
  }

  @Test
  fun testSystemNodesAreNotDrawn() = runTest {
    treeSettings.hideSystemNodes = false
    testScheduler.advanceUntilIdle()

    val layoutAppcompat =
      ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val systemNodesWindow =
      viewWindow(ROOT, 0, 0, 100, 100) {
        view(VIEW2, 0, 0, 10, 10) {}
        view(VIEW1, 0, 0, 50, 50, layout = layoutAppcompat) {}
      }
    inspectorModel.update(systemNodesWindow, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()

    val expectedInstructions1 =
      listOf(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(0, 0, 10, 10),
          color = BASE_COLOR_ARGB,
        ),
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(0, 0, 50, 50),
          color = BASE_COLOR_ARGB,
        ),
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(0, 0, 100, 100),
          color = BASE_COLOR_ARGB,
        ),
      )
    val instructions1 = onDeviceRendererModel.visibleNodes.first()
    assertThat(instructions1).isEqualTo(expectedInstructions1)

    treeSettings.hideSystemNodes = true
    inspectorModel.update(systemNodesWindow, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()

    val expectedInstructions2 =
      listOf(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(0, 0, 10, 10),
          color = BASE_COLOR_ARGB,
        )
      )
    val instructions2 = onDeviceRendererModel.visibleNodes.first()
    assertThat(instructions2).isEqualTo(expectedInstructions2)
  }

  @Test
  fun testSystemNodesAreNotSelected() = runTest {
    treeSettings.hideSystemNodes = false
    testScheduler.advanceUntilIdle()

    val layoutAppcompat =
      ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val systemNodesWindow =
      viewWindow(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 0, 0, 50, 50, layout = layoutAppcompat) {}
        view(VIEW2, 0, 0, 10, 10) {}
      }
    inspectorModel.update(systemNodesWindow, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()

    onDeviceRendererModel.selectNode(5.0, 5.0, ROOT)
    val instructions1 = onDeviceRendererModel.selectedNode.first()
    assertThat(instructions1)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(0, 0, 50, 50),
          color = SELECTION_COLOR_ARGB,
        )
      )

    treeSettings.hideSystemNodes = true
    onDeviceRendererModel.selectNode(5.0, 5.0, ROOT)
    val instructions2 = onDeviceRendererModel.selectedNode.first()
    assertThat(instructions2)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(0, 0, 10, 10),
          color = SELECTION_COLOR_ARGB,
        )
      )
  }

  @Test
  fun testSystemNodesAreNotHovered() = runTest {
    treeSettings.hideSystemNodes = false
    testScheduler.advanceUntilIdle()

    val layoutAppcompat =
      ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val systemNodesWindow =
      viewWindow(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 0, 0, 50, 50, layout = layoutAppcompat) {}
        view(VIEW2, 0, 0, 10, 10) {}
      }
    inspectorModel.update(systemNodesWindow, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()

    onDeviceRendererModel.hoverNode(5.0, 5.0, ROOT)
    val instructions1 = onDeviceRendererModel.hoveredNode.first()
    assertThat(instructions1)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(0, 0, 50, 50),
          color = HOVER_COLOR_ARGB,
        )
      )

    treeSettings.hideSystemNodes = true
    onDeviceRendererModel.hoverNode(5.0, 5.0, ROOT)
    val instructions2 = onDeviceRendererModel.hoveredNode.first()
    assertThat(instructions2)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = Rectangle(0, 0, 10, 10),
          color = HOVER_COLOR_ARGB,
        )
      )
  }

  @Test
  fun testRenderSettingsDrawBorders() = runTest {
    inspectorModel.hoveredNode = inspectorModel[VIEW1]
    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)
    testScheduler.advanceUntilIdle()

    val expectedVisibleNodes =
      listOf(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[VIEW1]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[COMPOSE1]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[ROOT]!!.layoutBounds,
          color = BASE_COLOR_ARGB,
        ),
      )

    val visibleNodes1 = onDeviceRendererModel.visibleNodes.first()
    assertThat(visibleNodes1).isEqualTo(expectedVisibleNodes)

    val hoveredNode1 = onDeviceRendererModel.hoveredNode.first()
    assertThat(hoveredNode1)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[VIEW1]!!.layoutBounds,
          color = HOVER_COLOR_ARGB,
        )
      )

    // Test borders are not drawn.
    renderSettings.drawBorders = false

    val visibleNodes2 = onDeviceRendererModel.visibleNodes.first()
    assertThat(visibleNodes2).isEmpty()

    val hoveredNode2 = onDeviceRendererModel.hoveredNode.first()
    assertThat(hoveredNode2)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[VIEW1]!!.layoutBounds,
          color = HOVER_COLOR_ARGB,
        )
      )

    // Verify selected node is not affected.
    val selectedNode1 = onDeviceRendererModel.selectedNode.first()
    assertThat(selectedNode1)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[VIEW1]!!.layoutBounds,
          color = SELECTION_COLOR_ARGB,
        )
      )

    // Test borders are re-drawn.
    renderSettings.drawBorders = true

    val visibleNodes3 = onDeviceRendererModel.visibleNodes.first()
    assertThat(visibleNodes3).isEqualTo(expectedVisibleNodes)

    val hoveredNode3 = onDeviceRendererModel.hoveredNode.first()
    assertThat(hoveredNode3)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[VIEW1]!!.layoutBounds,
          color = HOVER_COLOR_ARGB,
        )
      )

    // Test borders are not drawn during updates.
    renderSettings.drawBorders = false

    val xrWindow = viewWindow(ROOT, 0, 0, 100, 200, isXr = true) { view(VIEW1, 25, 30, 50, 50) {} }
    inspectorModel.update(xrWindow, listOf(ROOT), 0)
    inspectorModel.hoveredNode = inspectorModel[VIEW1]
    testScheduler.advanceUntilIdle()

    val visibleNodes4 = onDeviceRendererModel.visibleNodes.first()
    assertThat(visibleNodes4).isEmpty()

    val hoveredNode4 = onDeviceRendererModel.hoveredNode.first()
    assertThat(hoveredNode4)
      .isEqualTo(
        DrawInstruction(
          rootViewId = ROOT,
          bounds = inspectorModel[VIEW1]!!.layoutBounds,
          color = HOVER_COLOR_ARGB,
        )
      )

    // Test listener is removed on dispose.
    Disposer.dispose(onDeviceRendererModel)
    assertThat(renderSettings.modificationListeners).isEmpty()
  }
}
