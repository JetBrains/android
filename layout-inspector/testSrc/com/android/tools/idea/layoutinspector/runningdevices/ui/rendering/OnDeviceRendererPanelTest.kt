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

import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnDeviceRendererPanelTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()

  private lateinit var inspectorModel: InspectorModel
  private lateinit var renderModel: OnDeviceRendererModel

  @Before
  fun setUp() {
    inspectorModel =
      model(disposableRule.disposable) {
        view(ROOT, 0, 0, 100, 100) {
          view(VIEW1, 10, 15, 25, 25) {}
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
        }
      }

    renderModel =
      OnDeviceRendererModel(
        parentDisposable = disposableRule.disposable,
        inspectorModel = inspectorModel,
        FakeTreeSettings(),
      )
  }

  @Test
  fun testInitEnablesOnDeviceRendering() = runTest {
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
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = backgroundScope,
      client = onDeviceRenderingClient,
      renderModel = renderModel,
    )

    yield()

    assertThat(receivedMessages).hasSize(5)
    assertThat(receivedMessages[0]).isEqualTo(enableOnDeviceRenderingCommand)
  }

  @Test
  fun testInterceptClicksEvents() = runTest {
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
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = backgroundScope,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
      )

    yield()

    onDeviceRenderer.interceptClicks = true

    yield()

    assertThat(receivedMessages).hasSize(6)
    assertThat(receivedMessages[5]).isEqualTo(enableInterceptTouchEventsCommand)

    onDeviceRenderer.interceptClicks = true

    yield()

    // Verify that setting the state to true again does not send another message.
    assertThat(receivedMessages).hasSize(6)
  }

  @Test
  fun testModelSelectionChange() = runTest {
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
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = backgroundScope,
      client = onDeviceRenderingClient,
      renderModel = renderModel,
    )

    yield()

    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)

    yield()

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds = listOf(inspectorModel[VIEW1]!!.layoutBounds),
          type = LayoutInspectorViewProtocol.DrawCommand.Type.SELECTED_NODES,
        )
        .toByteArray()
    assertThat(receivedMessages).hasSize(6)
    assertThat(receivedMessages[5]).isEqualTo(expectedCommand)
  }

  @Test
  fun testModelHoverChange() = runTest {
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
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = backgroundScope,
      client = onDeviceRenderingClient,
      renderModel = renderModel,
    )

    yield()

    inspectorModel.hoveredNode = inspectorModel[VIEW1]

    yield()

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds = listOf(inspectorModel[VIEW1]!!.layoutBounds),
          type = LayoutInspectorViewProtocol.DrawCommand.Type.HOVERED_NODES,
        )
        .toByteArray()
    assertThat(receivedMessages).hasSize(6)
    assertThat(receivedMessages[5]).isEqualTo(expectedCommand)
  }

  @Test
  fun testModelVisibleNodesChange() = runTest {
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
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    OnDeviceRendererPanel(
      disposable = disposableRule.disposable,
      scope = backgroundScope,
      client = onDeviceRenderingClient,
      renderModel = renderModel,
    )

    yield()

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds =
            listOf(
              inspectorModel[VIEW1]!!.layoutBounds,
              inspectorModel[COMPOSE1]!!.layoutBounds,
              inspectorModel[ROOT]!!.layoutBounds,
            ),
          type = LayoutInspectorViewProtocol.DrawCommand.Type.VISIBLE_NODES,
        )
        .toByteArray()
    assertThat(receivedMessages).hasSize(5)
    assertThat(receivedMessages[4]).isEqualTo(expectedCommand)
  }

  @Test
  fun testSelectedNodeReceived() = runTest {
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
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = backgroundScope,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
      )

    yield()

    val touchEvent =
      buildUserInputEventProto(
        rootId = ROOT,
        x = 15f,
        y = 55f,
        type = LayoutInspectorViewProtocol.UserInputEvent.Type.SELECTION,
      )
    onDeviceRenderer.interceptClicks = true
    onDeviceRenderingClient.handleEvent(touchEvent)

    yield()

    assertThat(inspectorModel.selection).isEqualTo(inspectorModel[COMPOSE1])
  }

  @Test
  fun testHoverNodeReceived() = runTest {
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
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        scope = backgroundScope,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
      )

    yield()

    val touchEvent =
      buildUserInputEventProto(
        rootId = ROOT,
        x = 15f,
        y = 55f,
        type = LayoutInspectorViewProtocol.UserInputEvent.Type.HOVER,
      )
    onDeviceRenderer.interceptClicks = true
    onDeviceRenderingClient.handleEvent(touchEvent)

    yield()

    assertThat(inspectorModel.hoveredNode).isEqualTo(inspectorModel[COMPOSE1])
  }

  @Test
  fun testDisposeCancelsScope() = runTest {
    val messenger =
      object : AppInspectorMessenger {
        override suspend fun sendRawCommand(rawData: ByteArray) = ByteArray(0)

        override val eventFlow: Flow<ByteArray> = emptyFlow()
        override val scope: CoroutineScope = CoroutineScope(Job())
      }
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val onDeviceRenderer =
      OnDeviceRendererPanel(
        disposable = disposableRule.disposable,
        // Use the testScope instead of backgroundScope to test that all the running coroutines are
        // canceled by disposing.
        scope = this,
        client = onDeviceRenderingClient,
        renderModel = renderModel,
      )

    yield()

    Disposer.dispose(onDeviceRenderer)
  }
}
