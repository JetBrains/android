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
package com.android.tools.idea.layoutinspector.pipeline.view

import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.InputEvent
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.DrawInstruction
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.buildDrawNodeCommand
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.buildUserInputEventProto
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.enableOnDeviceRenderingCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.UserInputEvent
import com.google.common.truth.Truth.assertThat
import java.awt.Rectangle
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test

class OnDeviceRenderingClientTest {
  private lateinit var receivedCommands: MutableList<ByteArray>
  private lateinit var onDeviceRenderingClient: OnDeviceRenderingClient

  @Before
  fun setUp() {
    receivedCommands = mutableListOf<ByteArray>()
    val messenger =
      object : AppInspectorMessenger {
        override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
          receivedCommands.add(rawData)
          return ByteArray(0)
        }

        override val eventFlow: Flow<ByteArray> = emptyFlow()
        override val scope: CoroutineScope = CoroutineScope(Job())
      }

    onDeviceRenderingClient = OnDeviceRenderingClient(messenger)
  }

  @Test
  fun testHandleSelectionEvent() = runTest {
    val selectionEventProto =
      buildUserInputEventProto(rootId = 1L, x = 1f, y = 1f, type = UserInputEvent.Type.SELECTION)

    // Launch collector coroutine, these events are ephemeral so it needs to be started before
    // sending the event.
    launch {
      val receivedEvent = onDeviceRenderingClient.selectionEvents.first()
      assertThat(receivedEvent).isEqualTo(InputEvent(rootId = 1L, x = 1f, y = 1f))
    }

    testScheduler.advanceUntilIdle()

    onDeviceRenderingClient.handleEvent(selectionEventProto)
  }

  @Test
  fun testHandleHoverEvent() = runTest {
    val touchEventProto =
      buildUserInputEventProto(rootId = 1L, x = 1f, y = 1f, type = UserInputEvent.Type.HOVER)

    // Launch collector coroutine, these events are ephemeral so it needs to be started before
    // sending the event.
    launch {
      val receivedEvent = onDeviceRenderingClient.hoverEvents.first()
      assertThat(receivedEvent).isEqualTo(InputEvent(rootId = 1L, x = 1f, y = 1f))
    }

    testScheduler.advanceUntilIdle()

    onDeviceRenderingClient.handleEvent(touchEventProto)
  }

  @Test
  fun testHandleRightClickEvent() = runTest {
    val rightClickEventProto =
      buildUserInputEventProto(rootId = 1L, x = 1f, y = 1f, type = UserInputEvent.Type.RIGHT_CLICK)

    // Launch collector coroutine, these events are ephemeral so it needs to be started before
    // sending the event.
    launch {
      val receivedEvent = onDeviceRenderingClient.rightClickEvents.first()
      assertThat(receivedEvent).isEqualTo(InputEvent(rootId = 1L, x = 1f, y = 1f))
    }

    testScheduler.advanceUntilIdle()

    onDeviceRenderingClient.handleEvent(rightClickEventProto)
  }

  @Test
  fun testOldSelectionEventsAreNotReceived() = runTest {
    val selectionEventProto =
      buildUserInputEventProto(rootId = 1L, x = 1f, y = 1f, type = UserInputEvent.Type.SELECTION)

    // Send event before the collector is started.
    onDeviceRenderingClient.handleEvent(selectionEventProto)

    launch {
      withTimeout(100) {
        onDeviceRenderingClient.selectionEvents.first()
        fail("No event should be received.")
      }
    }
  }

  @Test
  fun testOldHoverEventsAreNotReceived() = runTest {
    val hoverEventProto =
      buildUserInputEventProto(rootId = 1L, x = 1f, y = 1f, type = UserInputEvent.Type.HOVER)

    // Send event before the collector is started.
    onDeviceRenderingClient.handleEvent(hoverEventProto)

    launch {
      withTimeout(100) {
        onDeviceRenderingClient.hoverEvents.first()
        fail("No event should be received.")
      }
    }
  }

  @Test
  fun testOldRightClickEventsAreNotReceived() = runTest {
    val rightClickEventProto =
      buildUserInputEventProto(rootId = 1L, x = 1f, y = 1f, type = UserInputEvent.Type.RIGHT_CLICK)

    // Send event before the collector is started.
    onDeviceRenderingClient.handleEvent(rightClickEventProto)

    launch {
      withTimeout(100) {
        onDeviceRenderingClient.rightClickEvents.first()
        fail("No event should be received.")
      }
    }
  }

  @Test
  fun testEnableOnDeviceRendering() = runTest {
    onDeviceRenderingClient.enableOnDeviceRendering(true)

    val expectedCommand = enableOnDeviceRenderingCommand

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawSelectedNode(): Unit = runTest {
    val drawInstructions = DrawInstruction(1L, Rectangle(), 1)
    onDeviceRenderingClient.drawSelectedNode(drawInstructions)

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = 1L,
          bounds = listOf(Rectangle()),
          color = 1,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.SELECTED_NODES,
        )
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawHoveredNode(): Unit = runTest {
    val drawInstructions = DrawInstruction(1L, Rectangle(), 1)
    onDeviceRenderingClient.drawHoveredNode(drawInstructions)

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = 1L,
          bounds = listOf(Rectangle()),
          color = 1,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.HOVERED_NODES,
        )
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawVisibleNodes(): Unit = runTest {
    val drawInstructions = DrawInstruction(1L, Rectangle(), 1)
    onDeviceRenderingClient.drawVisibleNodes(listOf(drawInstructions))

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = 1L,
          bounds = listOf(Rectangle()),
          color = 1,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.VISIBLE_NODES,
        )
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawRecomposingNodes(): Unit = runTest {
    val drawInstructions = DrawInstruction(1L, Rectangle(), 1)
    onDeviceRenderingClient.drawRecomposingNodes(listOf(drawInstructions))

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = 1L,
          bounds = listOf(Rectangle()),
          color = 1,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.RECOMPOSING_NODES,
        )
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawSelectedNodeNull(): Unit = runTest {
    val drawInstructions = null
    onDeviceRenderingClient.drawSelectedNode(drawInstructions)

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = 1L,
          bounds = emptyList(),
          color = 1,
          LayoutInspectorViewProtocol.DrawCommand.Type.SELECTED_NODES,
        )
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawHoveredNodeNull(): Unit = runTest {
    val drawInstructions = null
    onDeviceRenderingClient.drawHoveredNode(drawInstructions)

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = 1L,
          bounds = emptyList(),
          color = 1,
          LayoutInspectorViewProtocol.DrawCommand.Type.HOVERED_NODES,
        )
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawVisibleNodesEmpty(): Unit = runTest {
    onDeviceRenderingClient.drawVisibleNodes(emptyList())

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = 1L,
          bounds = emptyList(),
          color = 1,
          LayoutInspectorViewProtocol.DrawCommand.Type.VISIBLE_NODES,
        )
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawRecomposingNodesEmpty(): Unit = runTest {
    onDeviceRenderingClient.drawRecomposingNodes(emptyList())

    val expectedCommand =
      buildDrawNodeCommand(
          rootId = 1L,
          bounds = emptyList(),
          color = 1,
          LayoutInspectorViewProtocol.DrawCommand.Type.RECOMPOSING_NODES,
        )
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testInterceptTouchEvent(): Unit = runTest {
    onDeviceRenderingClient.interceptTouchEvents(true)

    val expectedCommand =
      Command.newBuilder()
        .apply {
          interceptTouchEventsCommand =
            LayoutInspectorViewProtocol.InterceptTouchEventsCommand.newBuilder()
              .setIntercept(true)
              .build()
        }
        .build()
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }
}
