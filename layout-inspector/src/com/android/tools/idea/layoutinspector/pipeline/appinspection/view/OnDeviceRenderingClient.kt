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
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import java.awt.Rectangle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A touch event received from the on-device renderer. */
data class TouchEvent(val x: Float, val y: Float)

/**
 * Draw instructions to be sent to the device.
 *
 * @param rootViewId The draw id of the root view on which to draw.
 * @param bounds The bounds of the node being rendered. In the case of regular View nodes, it's the
 *   bounds of the View itself. In the case of Compose node, it's the bounds of the composable.
 */
data class DrawInstruction(val rootViewId: Long, val bounds: Rectangle)

/**
 * Client that contains all the logic required to communicate with the on-device renderer.
 *
 * @param messenger The messenger used to communicate with the device. Currently using the
 *   [ViewLayoutInspectorClient] messenger.
 */
class OnDeviceRenderingClient(private val messenger: AppInspectorMessenger) {
  private val _touchEvents =
    MutableSharedFlow<TouchEvent?>(
      // When a new collector starts, it only sees future events.
      replay = 0,
      // Store only one event at a time.
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  val touchEvents = _touchEvents.asSharedFlow()

  fun handleEvent(event: Event): Boolean {
    return when (event.specializedCase) {
      Event.SpecializedCase.TOUCH_EVENT -> {
        handleTouchEvent(event.touchEvent)
        true
      }
      else -> false
    }
  }

  private fun handleTouchEvent(touchEvent: LayoutInspectorViewProtocol.TouchEvent) {
    val point = TouchEvent(touchEvent.x, touchEvent.y)
    _touchEvents.tryEmit(point)
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

  /** Send a command to the agent to draw the selected node. */
  suspend fun drawSelectedNode(instruction: DrawInstruction?) {
    val drawInstruction = instruction?.toProto()

    messenger.sendCommand {
      selectNodeCommand =
        LayoutInspectorViewProtocol.SelectNodeCommand.newBuilder()
          .apply { drawInstruction?.let { this.drawInstructions = it } }
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
