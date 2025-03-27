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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
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
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OnDeviceRendererPanelTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()

  private lateinit var inspectorModel: InspectorModel
  private lateinit var renderModel: OnDeviceRendererModel
  private lateinit var treeSettings: FakeTreeSettings

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

    renderModel =
      OnDeviceRendererModel(
        parentDisposable = disposableRule.disposable,
        inspectorModel = inspectorModel,
        treeSettings = treeSettings,
        renderSettings = FakeRenderSettings(),
      )
  }

  @Test
  fun testInitEnablesOnDeviceRendering() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      client = onDeviceRenderingClient,
      renderModel = renderModel,
      enableSendRightClicksToDevice = {},
    )

    testScheduler.advanceUntilIdle()

    assertThat(receivedMessages).hasSize(6)
    assertThat(receivedMessages[0]).isEqualTo(enableOnDeviceRenderingCommand)
  }

  @Test
  fun testInterceptClicksEvents() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = scope,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
        enableSendRightClicksToDevice = {},
      )

    testScheduler.advanceUntilIdle()

    receivedMessages.clear()
    onDeviceRenderer.interceptClicks = true

    testScheduler.advanceUntilIdle()

    assertThat(receivedMessages).hasSize(1)
    assertThat(receivedMessages.first()).isEqualTo(enableInterceptTouchEventsCommand)

    onDeviceRenderer.interceptClicks = true

    testScheduler.advanceUntilIdle()

    // Verify that setting the state to true again does not send another message.
    assertThat(receivedMessages).hasSize(1)
  }

  @Test
  fun testInterceptClicksTriggersRightClickToDevice() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val enableSendRightClicksToDeviceInvocations = mutableListOf<Boolean>()

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = scope,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
        enableSendRightClicksToDevice = { enableSendRightClicksToDeviceInvocations += it },
      )

    onDeviceRenderer.interceptClicks = true
    onDeviceRenderer.interceptClicks = false

    assertThat(enableSendRightClicksToDeviceInvocations).containsExactly(true, false)
  }

  @Test
  fun testModelSelectionChange() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      client = onDeviceRenderingClient,
      renderModel = renderModel,
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
          label = inspectorModel[VIEW1]?.unqualifiedName,
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

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      client = onDeviceRenderingClient,
      renderModel = renderModel,
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

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      client = onDeviceRenderingClient,
      renderModel = renderModel,
      enableSendRightClicksToDevice = {},
    )

    testScheduler.advanceUntilIdle()

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds =
            listOf(
              inspectorModel[VIEW1]!!.layoutBounds,
              inspectorModel[COMPOSE1]!!.layoutBounds,
              inspectorModel[ROOT]!!.layoutBounds,
            ),
          color = BASE_COLOR_ARGB,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.VISIBLE_NODES,
        )
        .toByteArray()
    assertThat(receivedMessages).hasSize(6)
    assertThat(receivedMessages[4]).isEqualTo(expectedCommand)
  }

  @Test
  fun testModelRecomposingNodesChange() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = scope,
      client = onDeviceRenderingClient,
      renderModel = renderModel,
      enableSendRightClicksToDevice = {},
    )

    testScheduler.advanceUntilIdle()

    treeSettings.showRecompositions = true

    val newWindow =
      viewWindow(ROOT, 0, 0, 100, 200) {
        compose(COMPOSE2, name = "compose-node", x = 0, y = 0, width = 50, height = 50) {}
      }
    var composeNode2 = newWindow.root.flattenedList().find { it.drawId == COMPOSE2 }!!
    composeNode2.recompositions.highlightCount = 100f
    inspectorModel.update(newWindow, listOf(ROOT), 0)

    testScheduler.advanceUntilIdle()

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds = listOf(inspectorModel[COMPOSE2]!!.layoutBounds),
          color = RECOMPOSITION_COLOR_RED_ARGB.setColorAlpha(160),
          type = LayoutInspectorViewProtocol.DrawCommand.Type.RECOMPOSING_NODES,
        )
        .toByteArray()
    assertThat(receivedMessages).hasSize(8)
    assertThat(receivedMessages[7]).isEqualTo(expectedCommand)
  }

  @Test
  fun testSelectedNodeReceived() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = scope,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
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
    onDeviceRenderer.interceptClicks = true
    onDeviceRenderingClient.handleEvent(touchEvent)

    testScheduler.advanceUntilIdle()

    assertThat(inspectorModel.selection).isEqualTo(inspectorModel[COMPOSE1])
  }

  @Test
  fun testHoverNodeReceived() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = scope,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
        enableSendRightClicksToDevice = {},
      )

    testScheduler.advanceUntilIdle()

    val touchEvent =
      buildUserInputEventProto(
        rootId = ROOT,
        x = 15f,
        y = 55f,
        type = LayoutInspectorViewProtocol.UserInputEvent.Type.HOVER,
      )
    onDeviceRenderer.interceptClicks = true
    onDeviceRenderingClient.handleEvent(touchEvent)

    testScheduler.advanceUntilIdle()

    assertThat(inspectorModel.hoveredNode).isEqualTo(inspectorModel[COMPOSE1])
  }

  @Test
  fun testRightClickShowsPopup() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = scope,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
        enableSendRightClicksToDevice = {},
      )

    onDeviceRenderer.interceptClicks = true

    testScheduler.advanceUntilIdle()

    val popupLatch = CountDownLatch(1)
    var lastPopup: FakeActionPopupMenu? = null
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, mock(), disposableRule.disposable)
    doAnswer { invocation ->
        lastPopup = FakeActionPopupMenu(invocation.getArgument(1))
        popupLatch.countDown()
        lastPopup
      }
      .whenever(ActionManager.getInstance())
      .createActionPopupMenu(anyString(), any<ActionGroup>())

    val parent = BorderLayoutPanel()
    parent.add(onDeviceRenderer)
    parent.size = Dimension(100, 100)
    onDeviceRenderer.size = Dimension(100, 100)
    val fakeUi = FakeUi(parent)
    fakeUi.render()

    // move the cursor
    fakeUi.mouse.moveTo(42, 42)
    withContext(Dispatchers.EDT) { fakeUi.layoutAndDispatchEvents() }

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
    popupLatch.await()

    lastPopup!!.assertSelectViewActionAndGotoDeclaration(COMPOSE1, ROOT)
    verify(lastPopup.popup).show(onDeviceRenderer, 42, 42)
  }

  @Test
  fun testDisposeCancelsScope() = runTest {
    val (_, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        // Use the testScope to test that all the running coroutines are canceled by disposing.
        scope = this,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
        enableSendRightClicksToDevice = {},
      )

    testScheduler.advanceUntilIdle()

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
