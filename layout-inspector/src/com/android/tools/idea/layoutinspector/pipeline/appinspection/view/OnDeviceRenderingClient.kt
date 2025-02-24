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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.DrawInstruction
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.UserInputEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A touch event received from the on-device renderer. */
data class InputEvent(val rootId: Long, val x: Float, val y: Float)

/**
 * Client that contains all the logic required to communicate with the on-device renderer.
 *
 * @param messenger The messenger used to communicate with the device. Currently using the
 *   [ViewLayoutInspectorClient] messenger.
 */
class OnDeviceRenderingClient(private val messenger: AppInspectorMessenger) {
  private val _selectionEvents =
    MutableSharedFlow<InputEvent?>(
      // When a new collector starts, it only sees future events.
      replay = 0,
      // Store only one event at a time.
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  val selectionEvents = _selectionEvents.asSharedFlow()

  private val _hoverEvents =
    MutableSharedFlow<InputEvent?>(
      // When a new collector starts, it only sees future events.
      replay = 0,
      // Store only one event at a time.
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  val hoverEvents = _hoverEvents.asSharedFlow()

  fun handleEvent(event: Event): Boolean {
    return when (event.specializedCase) {
      Event.SpecializedCase.USER_INPUT_EVENT -> {
        handleUserInputEvent(event.userInputEvent)
        true
      }
      else -> false
    }
  }

  private fun handleUserInputEvent(inputEvent: UserInputEvent) {
    val point = InputEvent(rootId = inputEvent.rootId, x = inputEvent.x, y = inputEvent.y)
    when (inputEvent.type) {
      UserInputEvent.Type.SELECTION -> _selectionEvents.tryEmit(point)
      UserInputEvent.Type.HOVER -> _hoverEvents.tryEmit(point)
      else -> throw IllegalArgumentException("Unknown user input type ${inputEvent.type}")
    }
  }

  /** Send a command to the agent to enable on-device rendering. */
  suspend fun enableOnDeviceRendering(enable: Boolean) {
    messenger.sendCommand {
      enableOnDeviceRenderingCommand =
        LayoutInspectorViewProtocol.EnableOnDeviceRenderingCommand.newBuilder()
          .setEnable(enable)
          .build()
    }
  }

  /** Send a command to the agent to enable intercepting touch events. */
  suspend fun interceptTouchEvents(intercept: Boolean) {
    messenger.sendCommand {
      interceptTouchEventsCommand =
        LayoutInspectorViewProtocol.InterceptTouchEventsCommand.newBuilder()
          .apply { this.intercept = intercept }
          .build()
    }
  }

  /** Send a command to the agent to draw the selected node. */
  suspend fun drawSelectedNode(instruction: DrawInstruction?) {
    sendDrawCommand(
      listOfNotNull(instruction),
      LayoutInspectorViewProtocol.DrawCommand.Type.SELECTED_NODES,
    )
  }

  /** Send a command to the agent to draw the hovered node. */
  suspend fun drawHoveredNode(instruction: DrawInstruction?) {
    sendDrawCommand(
      listOfNotNull(instruction),
      LayoutInspectorViewProtocol.DrawCommand.Type.HOVERED_NODES,
    )
  }

  /** Send a command to the agent to draw the visible nodes. */
  suspend fun drawVisibleNodes(instructions: List<DrawInstruction>) {
    sendDrawCommand(instructions, LayoutInspectorViewProtocol.DrawCommand.Type.VISIBLE_NODES)
  }

  /** Send a command to the agent to draw the recomposition highlights. */
  suspend fun drawRecomposingNodes(instructions: List<DrawInstruction>) {
    sendDrawCommand(instructions, LayoutInspectorViewProtocol.DrawCommand.Type.RECOMPOSING_NODES)
  }

  private suspend fun sendDrawCommand(
    instructions: List<DrawInstruction>,
    type: LayoutInspectorViewProtocol.DrawCommand.Type,
  ) {
    val drawInstructions = instructions.map { it.toProto() }

    messenger.sendCommand {
      drawCommand =
        LayoutInspectorViewProtocol.DrawCommand.newBuilder()
          .apply {
            this.type = type
            if (drawInstructions.isNotEmpty()) {
              addAllDrawInstructions(drawInstructions)
            }
          }
          .build()
    }
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
