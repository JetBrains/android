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
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.DrawInstruction
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.TouchEvent
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
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
import kotlinx.coroutines.yield
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
  fun testHandleEvent() = runTest {
    val touchEvent =
      LayoutInspectorViewProtocol.Event.newBuilder()
        .setTouchEvent(
          LayoutInspectorViewProtocol.TouchEvent.newBuilder()
            .apply {
              x = 1f
              y = 1f
            }
            .build()
        )
        .build()

    // Launch collector coroutine
    val job = launch {
      val receivedEvent = onDeviceRenderingClient.touchEvents.first()
      assertThat(receivedEvent).isEqualTo(TouchEvent(1f, 1f))
    }

    // Give the collector coroutine a chance to start collecting, so we don't emit too early.
    yield()

    onDeviceRenderingClient.handleEvent(touchEvent)

    // Wait for the collector coroutine to finish
    job.join()
  }

  @Test
  fun testOldTouchEventsAreNotReceived() = runTest {
    val touchEvent =
      LayoutInspectorViewProtocol.Event.newBuilder()
        .setTouchEvent(
          LayoutInspectorViewProtocol.TouchEvent.newBuilder()
            .apply {
              x = 1f
              y = 1f
            }
            .build()
        )
        .build()

    // Send event before the collector is started.
    onDeviceRenderingClient.handleEvent(touchEvent)

    // Launch collector coroutine.
    val job = launch {
      withTimeout(100) {
        onDeviceRenderingClient.touchEvents.first()
        fail("No event should be received.")
      }
    }

    job.join()
  }

  @Test
  fun testEnableOnDeviceRendering(): Unit = runTest {
    onDeviceRenderingClient.enableOnDeviceRendering(true)

    val expectedCommand =
      Command.newBuilder()
        .apply {
          enableOnDeviceRenderingCommand =
            LayoutInspectorViewProtocol.EnableOnDeviceRenderingCommand.newBuilder()
              .setEnable(true)
              .build()
        }
        .build()
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawSelectedNode(): Unit = runTest {
    val drawInstructions = DrawInstruction(1, Rectangle())
    onDeviceRenderingClient.drawSelectedNode(drawInstructions)

    val expectedCommand =
      Command.newBuilder()
        .apply {
          selectNodeCommand =
            LayoutInspectorViewProtocol.SelectNodeCommand.newBuilder()
              .setDrawInstructions(drawInstructions.toProto())
              .build()
        }
        .build()
        .toByteArray()

    assertThat(receivedCommands.size).isEqualTo(1)
    assertThat(receivedCommands.first()).isEqualTo(expectedCommand)
  }

  @Test
  fun testDrawSelectedNodeNull(): Unit = runTest {
    val drawInstructions = null
    onDeviceRenderingClient.drawSelectedNode(drawInstructions)

    val expectedCommand =
      Command.newBuilder()
        .apply {
          selectNodeCommand = LayoutInspectorViewProtocol.SelectNodeCommand.newBuilder().build()
        }
        .build()
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

private fun DrawInstruction.toProto(): LayoutInspectorViewProtocol.DrawInstruction {
  val boundsRect =
    LayoutInspectorViewProtocol.Rect.newBuilder()
      .apply {
        x = bounds.x
        y = bounds.y
        w = bounds.width
        h = bounds.height
      }
      .build()

  return LayoutInspectorViewProtocol.DrawInstruction.newBuilder()
    .setRootId(rootViewId)
    .setBounds(boundsRect)
    .build()
}
