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

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.LABEL_FONT_SIZE
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.RenderingDimensions.EMPHASIZED_BORDER_THICKNESS
import com.android.tools.idea.layoutinspector.model.RenderingDimensions.NORMAL_BORDER_THICKNESS
import com.android.tools.idea.layoutinspector.model.RenderingDimensions.RECOMPOSITION_BORDER_THICKNESS
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.android.tools.idea.layoutinspector.ui.BASE_COLOR_ARGB
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.ui.HOVER_COLOR_ARGB
import com.android.tools.idea.layoutinspector.ui.SELECTION_COLOR_ARGB
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RECOMPOSITION_COLOR_RED_ARGB
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.viewWindow
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.TimeoutException
import javax.swing.JComponent
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class OnDeviceRendererPanelTest {
  @get:Rule val runInEdt = EdtRule()
  @get:Rule val disposableRule = DisposableRule()

  private lateinit var inspectorModel: InspectorModel
  private lateinit var renderModel: EmbeddedRendererModel
  private lateinit var treeSettings: FakeTreeSettings

  private var navigateToInvocations = 0

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
    navigateToInvocations = 0

    renderModel =
      EmbeddedRendererModel(
        parentDisposable = disposableRule.disposable,
        inspectorModel = inspectorModel,
        treeSettings = treeSettings,
        renderSettings = FakeRenderSettings(),
        navigateToSelectedViewOnDoubleClick = { navigateToInvocations += 1 },
      )
  }

  @Test
  @RunsInEdt
  fun testMouseEventsAreDispatchedToParent() = runTest {
    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
      )

    val onDeviceRendererPanel =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = scope,
        model = onDeviceRendererModel,
        enableSendRightClicksToDevice = {},
      )

    var lastMousePosition: Point? = null

    val parent =
      object : BorderLayoutPanel() {
        private inner class MouseListener : MouseAdapter() {
          override fun mouseMoved(e: MouseEvent) {
            lastMousePosition = Point(e.x, e.y)
          }
        }

        init {
          addMouseMotionListener(MouseListener())
        }
      }

    parent.add(onDeviceRendererPanel)
    parent.size = Dimension(100, 100)
    onDeviceRendererPanel.size = Dimension(100, 100)
    val fakeUi = FakeUi(onDeviceRendererPanel)
    fakeUi.render()

    // move the cursor
    fakeUi.mouse.moveTo(42, 42)
    fakeUi.layoutAndDispatchEvents()

    assertThat(lastMousePosition).isEqualTo(Point(42, 42))
  }

  @Test
  fun testInterceptClicksEvents() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    val enableRightClicksInvocations = mutableListOf<Boolean>()

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      model = onDeviceRendererModel,
      enableSendRightClicksToDevice = { enableRightClicksInvocations.add(it) },
    )

    testScheduler.advanceUntilIdle()

    assertThat(enableRightClicksInvocations).containsExactly(false)

    receivedMessages.clear()
    renderModel.setInterceptClicks(true)
    testScheduler.advanceUntilIdle()

    assertThat(receivedMessages).hasSize(1)
    assertThat(receivedMessages.first()).isEqualTo(enableInterceptTouchEventsCommand)
    assertThat(enableRightClicksInvocations).containsExactly(false, true)

    renderModel.setInterceptClicks(true)
    testScheduler.advanceUntilIdle()

    // Verify that setting the state to true again does not send another message.
    assertThat(receivedMessages).hasSize(1)
    assertThat(enableRightClicksInvocations).containsExactly(false, true)

    renderModel.setInterceptClicks(false)
    testScheduler.advanceUntilIdle()

    assertThat(receivedMessages).hasSize(2)
    assertThat(enableRightClicksInvocations).containsExactly(false, true, false)
  }

  @Test
  fun testModelSelectionChange() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      model = onDeviceRendererModel,
      enableSendRightClicksToDevice = {},
    )

    testScheduler.advanceUntilIdle()

    receivedMessages.clear()
    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)

    testScheduler.advanceUntilIdle()

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds = listOf(inspectorModel[VIEW1]!!.layoutBounds),
          color = SELECTION_COLOR_ARGB,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.SELECTED_NODES,
          label =
            inspectorModel[VIEW1]?.unqualifiedName?.let {
              DrawInstruction.Label(text = it, size = LABEL_FONT_SIZE)
            },
          strokeThickness = EMPHASIZED_BORDER_THICKNESS,
        )
        .toByteArray()
    assertThat(receivedMessages).hasSize(1)
    assertThat(receivedMessages.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testModelHoverChange() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      model = onDeviceRendererModel,
      enableSendRightClicksToDevice = {},
    )

    testScheduler.advanceUntilIdle()

    receivedMessages.clear()
    inspectorModel.hoveredNode = inspectorModel[VIEW1]

    testScheduler.advanceUntilIdle()

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds = listOf(inspectorModel[VIEW1]!!.layoutBounds),
          color = HOVER_COLOR_ARGB,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.HOVERED_NODES,
          label = null,
          strokeThickness = EMPHASIZED_BORDER_THICKNESS,
        )
        .toByteArray()
    assertThat(receivedMessages).hasSize(1)
    assertThat(receivedMessages.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testModelVisibleNodesChange() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      model = onDeviceRendererModel,
      enableSendRightClicksToDevice = {},
    )

    testScheduler.advanceUntilIdle()

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds =
            listOf(
              inspectorModel[COMPOSE1]!!.layoutBounds,
              inspectorModel[VIEW1]!!.layoutBounds,
              inspectorModel[ROOT]!!.layoutBounds,
            ),
          color = BASE_COLOR_ARGB,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.VISIBLE_NODES,
          label = null,
          strokeThickness = NORMAL_BORDER_THICKNESS,
        )
        .toByteArray()
    assertThat(receivedMessages).hasSize(8)
    assertThat(receivedMessages[4]).isEqualTo(expectedCommand)
  }

  @Test
  fun testModelRecomposingNodesChange() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      model = onDeviceRendererModel,
      enableSendRightClicksToDevice = {},
    )

    testScheduler.advanceUntilIdle()

    treeSettings.showRecompositions = true

    val newWindow =
      viewWindow(ROOT, 0, 0, 100, 200) {
        compose(COMPOSE2, name = "compose-node", x = 0, y = 0, width = 50, height = 50) {}
      }
    val composeNode2 = newWindow.root.flattenedList().find { it.drawId == COMPOSE2 }!!
    composeNode2.recompositions.highlightCount = 100f
    inspectorModel.update(newWindow, listOf(ROOT), 0)

    testScheduler.advanceUntilIdle()

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds = listOf(inspectorModel[COMPOSE2]!!.layoutBounds),
          color = RECOMPOSITION_COLOR_RED_ARGB.setColorAlpha(160),
          type = LayoutInspectorViewProtocol.DrawCommand.Type.RECOMPOSING_NODES,
          label = null,
          strokeThickness = RECOMPOSITION_BORDER_THICKNESS,
        )
        .toByteArray()
    assertThat(receivedMessages).hasSize(10)
    assertThat(receivedMessages[9]).isEqualTo(expectedCommand)
  }

  @Test
  fun testSelectedNodeReceived() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      model = onDeviceRendererModel,
      enableSendRightClicksToDevice = {},
    )

    testScheduler.advanceUntilIdle()

    val touchEvent =
      buildUserInputEventProto(
        rootId = ROOT,
        x = 15f,
        y = 55f,
        type = LayoutInspectorViewProtocol.UserInputEvent.Type.SELECTION,
      )
    renderModel.setInterceptClicks(true)
    testScheduler.advanceUntilIdle()

    onDeviceRenderingClient.handleEvent(touchEvent)

    testScheduler.advanceUntilIdle()

    assertThat(inspectorModel.selection).isEqualTo(inspectorModel[COMPOSE1])
  }

  @Test
  @RunsInEdt
  fun testHoverNodeReceived() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = scope,
        model = onDeviceRendererModel,
        enableSendRightClicksToDevice = {},
      )

    val parent = BorderLayoutPanel()
    parent.add(onDeviceRenderer)
    parent.size = Dimension(100, 100)
    onDeviceRenderer.size = Dimension(100, 100)
    val fakeUi = FakeUi(parent)
    fakeUi.render()

    // move the cursor on the panel, to enable handling hover
    fakeUi.mouse.moveTo(42, 42)
    fakeUi.layoutAndDispatchEvents()

    testScheduler.advanceUntilIdle()

    val touchEvent =
      buildUserInputEventProto(
        rootId = ROOT,
        x = 15f,
        y = 55f,
        type = LayoutInspectorViewProtocol.UserInputEvent.Type.HOVER,
      )
    renderModel.setInterceptClicks(true)
    testScheduler.advanceUntilIdle()

    onDeviceRenderingClient.handleEvent(touchEvent)

    testScheduler.advanceUntilIdle()

    assertThat(inspectorModel.hoveredNode).isEqualTo(inspectorModel[COMPOSE1])

    // move the cursor outside the panel, should clear hover
    fakeUi.mouse.moveTo(200, 200)
    fakeUi.layoutAndDispatchEvents()

    assertThat(inspectorModel.hoveredNode).isEqualTo(null)
  }

  @Test
  fun testDoubleClickNodeReceived() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      model = onDeviceRendererModel,
      enableSendRightClicksToDevice = {},
    )

    testScheduler.advanceUntilIdle()

    val touchEvent =
      buildUserInputEventProto(
        rootId = ROOT,
        x = 15f,
        y = 55f,
        type = LayoutInspectorViewProtocol.UserInputEvent.Type.DOUBLE_CLICK,
      )
    renderModel.setInterceptClicks(true)
    testScheduler.advanceUntilIdle()

    onDeviceRenderingClient.handleEvent(touchEvent)

    testScheduler.advanceUntilIdle()

    assertThat(inspectorModel.selection).isEqualTo(inspectorModel[COMPOSE1])
    assertThat(navigateToInvocations).isEqualTo(1)
  }

  @Test
  @RunsInEdt
  fun testRightClickShowsPopup() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    var rightClickInvocations = 0
    var rightClickNodes: List<ViewNode>? = null
    var rightClickCoordinates: Point? = null
    var rightClickSelectedNode: ViewNode? = null

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = scope,
        model = onDeviceRendererModel,
        enableSendRightClicksToDevice = {},
        showRightClickMenu = {
          _: JComponent,
          selectedNode: ViewNode?,
          nodes: List<ViewNode>,
          point: Point ->
          rightClickInvocations += 1
          rightClickNodes = nodes
          rightClickCoordinates = point
          rightClickSelectedNode = selectedNode
        },
      )

    renderModel.setInterceptClicks(true)
    testScheduler.advanceUntilIdle()

    val parent = BorderLayoutPanel()
    parent.add(onDeviceRenderer)
    parent.size = Dimension(100, 100)
    onDeviceRenderer.size = Dimension(100, 100)
    val fakeUi = FakeUi(parent)
    fakeUi.render()

    // move the cursor
    fakeUi.mouse.moveTo(42, 42)
    fakeUi.layoutAndDispatchEvents()

    assertThat(inspectorModel.selection).isNull()

    val rightClickEvent =
      buildUserInputEventProto(
        rootId = ROOT,
        x = 15f,
        y = 55f,
        type = LayoutInspectorViewProtocol.UserInputEvent.Type.RIGHT_CLICK,
      )
    // send right click from the device
    onDeviceRenderingClient.handleEvent(rightClickEvent)

    testScheduler.advanceUntilIdle()

    // wait for the popup to be shown.
    waitForCondition(2.seconds) { rightClickInvocations == 1 }

    assertThat(inspectorModel.selection).isEqualTo(inspectorModel[COMPOSE1])
    assertThat(rightClickSelectedNode).isEqualTo(inspectorModel[COMPOSE1])
    assertThat(rightClickNodes!!.map { it.drawId }).containsExactly(COMPOSE1, ROOT)
    assertThat(rightClickCoordinates).isEqualTo(Point(42, 42))

    rightClickNodes = null
    rightClickCoordinates = null
    rightClickSelectedNode = null

    // test that right click is ignored when cursor is not above the panel
    // move the cursor
    fakeUi.mouse.moveTo(200, 200)
    fakeUi.layoutAndDispatchEvents()

    val rightClickEvent2 =
      buildUserInputEventProto(
        rootId = ROOT,
        x = 15f,
        y = 55f,
        type = LayoutInspectorViewProtocol.UserInputEvent.Type.RIGHT_CLICK,
      )
    // send right click from the device
    onDeviceRenderingClient.handleEvent(rightClickEvent2)

    testScheduler.advanceUntilIdle()

    // wait for the popup to be shown.
    try {
      waitForCondition(2.seconds) { rightClickInvocations == 2 }
      fail("Right click menu was invoked.")
    } catch (_: TimeoutException) {}

    assertThat(inspectorModel.selection).isEqualTo(inspectorModel[COMPOSE1])
    assertThat(rightClickNodes).isNull()
    assertThat(rightClickCoordinates).isNull()
    assertThat(rightClickSelectedNode).isNull()
  }

  @Test
  @Ignore("b/459769542")
  fun testDisposeCancelsScope() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = disposableRule.disposable,
        // Use the testScope to test that all the running coroutines are canceled by disposing.
        scope = this,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        // Use the testScope to test that all the running coroutines are canceled by disposing.
        disposable = disposableRule.disposable,
        scope = this,
        model = onDeviceRendererModel,
        enableSendRightClicksToDevice = {},
      )

    testScheduler.advanceUntilIdle()

    Disposer.dispose(onDeviceRendererModel)
    Disposer.dispose(onDeviceRenderer)
  }
}

private fun buildMessenger(): Pair<MutableList<ByteArray>, AppInspectorMessenger> {
  val receivedMessages = mutableListOf<ByteArray>()
  val messenger =
    object : AppInspectorMessenger {
      override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
        receivedMessages.add(rawData)
        return ByteArray(0)
      }

      override val eventFlow: Flow<ByteArray> = emptyFlow()
      override val scope: CoroutineScope = CoroutineScope(Job())
    }

  return receivedMessages to messenger
}
