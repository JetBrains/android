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
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import java.awt.Rectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class OnDeviceRendererPanelTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()

  private lateinit var inspectorModel: InspectorModel
  private lateinit var renderModel: RenderModel

  @Before
  fun setUp() {
    inspectorModel =
      model(disposableRule.disposable) {
        view(ROOT, 0, 0, 100, 100) {
          view(VIEW1, 10, 15, 25, 25) { image() }
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
        }
      }

    renderModel =
      RenderModel(
        model = inspectorModel,
        notificationModel = mock(),
        treeSettings = FakeTreeSettings(showRecompositions = false),
        currentClientProvider = { DisconnectedClient },
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

    assertThat(receivedMessages).hasSize(1)
    assertThat(receivedMessages[0]).isEqualTo(enableOnDeviceRenderingCommand)
  }

  @Test
  fun testInterceptTouchEvents() = runTest {
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

    assertThat(receivedMessages).hasSize(2)
    assertThat(receivedMessages[1]).isEqualTo(enableInterceptTouchEventsCommand)

    onDeviceRenderer.interceptClicks = true

    yield()

    // Verify that setting the state to true again does not send another message.
    assertThat(receivedMessages).hasSize(2)
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

    assertThat(receivedMessages).hasSize(2)
    assertThat(receivedMessages[1])
      .isEqualTo(buildSelectNodeCommand(ROOT, inspectorModel[VIEW1]!!.layoutBounds))
  }

  @Test
  fun testTouchEventReceived() = runTest {
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
      LayoutInspectorViewProtocol.Event.newBuilder()
        .setTouchEvent(
          LayoutInspectorViewProtocol.TouchEvent.newBuilder()
            .apply {
              x = 15f
              y = 55f
            }
            .build()
        )
        .build()

    onDeviceRenderer.interceptClicks = true
    onDeviceRenderingClient.handleEvent(touchEvent)

    yield()

    assertThat(inspectorModel.selection).isEqualTo(inspectorModel[COMPOSE1])
  }

  @Test
  fun testDisposeRemoveListenersAndCancelsScope() = runTest {
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

    assertThat(inspectorModel.selectionListeners.size()).isEqualTo(1)

    Disposer.dispose(onDeviceRenderer)

    assertThat(inspectorModel.selectionListeners.size()).isEqualTo(0)
  }
}

private val enableOnDeviceRenderingCommand =
  Command.newBuilder()
    .apply {
      enableOnDeviceRenderingCommand =
        LayoutInspectorViewProtocol.EnableOnDeviceRenderingCommand.newBuilder()
          .setEnable(true)
          .build()
    }
    .build()
    .toByteArray()

private val enableInterceptTouchEventsCommand =
  Command.newBuilder()
    .apply {
      interceptTouchEventsCommand =
        LayoutInspectorViewProtocol.InterceptTouchEventsCommand.newBuilder()
          .setIntercept(true)
          .build()
    }
    .build()
    .toByteArray()

private fun buildSelectNodeCommand(rootId: Long, bounds: Rectangle): ByteArray {
  val boundsRect =
    LayoutInspectorViewProtocol.Rect.newBuilder()
      .apply {
        x = bounds.x
        y = bounds.y
        w = bounds.width
        h = bounds.height
      }
      .build()

  return Command.newBuilder()
    .apply {
      selectNodeCommand =
        LayoutInspectorViewProtocol.SelectNodeCommand.newBuilder()
          .apply {
            drawInstructions =
              LayoutInspectorViewProtocol.DrawInstruction.newBuilder()
                .apply {
                  this.rootId = rootId
                  this.bounds = boundsRect
                }
                .build()
          }
          .build()
    }
    .build()
    .toByteArray()
}
