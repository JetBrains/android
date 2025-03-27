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

import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import java.awt.Rectangle

val enableOnDeviceRenderingCommand =
  Command.newBuilder()
    .apply {
      enableOnDeviceRenderingCommand =
        LayoutInspectorViewProtocol.EnableOnDeviceRenderingCommand.newBuilder()
          .setEnable(true)
          .build()
    }
    .build()
    .toByteArray()

val enableInterceptTouchEventsCommand =
  Command.newBuilder()
    .apply {
      interceptTouchEventsCommand =
        LayoutInspectorViewProtocol.InterceptTouchEventsCommand.newBuilder()
          .setIntercept(true)
          .build()
    }
    .build()
    .toByteArray()

fun buildDrawNodeCommand(
  rootId: Long,
  bounds: List<Rectangle>,
  color: Int,
  type: LayoutInspectorViewProtocol.DrawCommand.Type,
  label: String? = null,
): Command {
  val drawInstructions =
    bounds.map {
      val rect =
        LayoutInspectorViewProtocol.Rect.newBuilder()
          .apply {
            x = it.x
            y = it.y
            w = it.width
            h = it.height
          }
          .build()

      LayoutInspectorViewProtocol.DrawInstruction.newBuilder()
        .apply {
          this.rootId = rootId
          this.bounds = rect
          this.color = color
          if (label != null) {
            this.label = label
          }
        }
        .build()
    }

  return Command.newBuilder()
    .apply {
      drawCommand =
        LayoutInspectorViewProtocol.DrawCommand.newBuilder()
          .apply {
            if (drawInstructions.isNotEmpty()) {
              this.addAllDrawInstructions(drawInstructions)
            }
            this.type = type
          }
          .build()
    }
    .build()
}

fun buildUserInputEventProto(
  rootId: Long,
  x: Float,
  y: Float,
  type: LayoutInspectorViewProtocol.UserInputEvent.Type,
): LayoutInspectorViewProtocol.Event {
  val userInputEvent =
    LayoutInspectorViewProtocol.UserInputEvent.newBuilder()
      .setType(type)
      .setRootId(rootId)
      .setX(x)
      .setY(y)
      .build()

  return LayoutInspectorViewProtocol.Event.newBuilder().setUserInputEvent(userInputEvent).build()
}

fun DrawInstruction.toProto(): LayoutInspectorViewProtocol.DrawInstruction {
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
