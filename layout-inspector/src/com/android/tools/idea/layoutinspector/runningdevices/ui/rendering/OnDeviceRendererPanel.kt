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

import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnDeviceRendererPanel(
  disposable: Disposable,
  scope: CoroutineScope,
  private val model: OnDeviceRendererModel,
  private val enableSendRightClicksToDevice: (enable: Boolean) -> Unit,
) : LayoutInspectorRenderer() {
  private val childScope = scope.createChildScope(parentDisposable = this)
  private val interceptClicks
    get() = model.interceptClicks.value

  private var lastMousePosition: Point? = null

  /** Indicates if the mouse is currently above the renderer. */
  private var isMouseOnPanel = false
    set(value) {
      if (field == value) {
        return
      }

      field = value
      if (!value && interceptClicks) {
        model.renderModel.clearHoverNode()
      }
      model.ignoreHoverEvents = !isMouseOnPanel
    }

  init {
    Disposer.register(disposable, this)
    isOpaque = false

    // Events are not dispatched to the parent if the child has a mouse listener. So we need to
    // manually forward them.
    ForwardingMouseListener(componentProvider = { parent }, shouldForward = { true }).also {
      addMouseListener(it)
      addMouseMotionListener(it)
      addMouseWheelListener(it)
    }
    MouseListener().also {
      addMouseListener(it)
      addMouseMotionListener(it)
    }

    childScope.launch {
      model.rightClick
        .mapNotNull { it }
        .collect { event ->
          val views =
            model.renderModel.rightClickNode(event.x.toDouble(), event.y.toDouble(), event.rootId)
          // There should always be a lastMousePosition available, if for some reason it's missing,
          // show the popup in them middle of the panel.
          val rightClickCoordinates = lastMousePosition ?: Point(width / 2, height / 2)
          withContext(Dispatchers.EDT) {
            showViewContextMenu(
              selectedView = model.inspectorModel.selection,
              views = views.toList(),
              inspectorModel = model.inspectorModel,
              source = this@OnDeviceRendererPanel,
              x = rightClickCoordinates.x,
              y = rightClickCoordinates.y,
            )
          }
        }
    }

    childScope.launch {
      model.interceptClicks.collect { interceptClicks ->
        enableSendRightClicksToDevice(interceptClicks)
      }
    }
  }

  override fun dispose() {}

  private inner class MouseListener : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      if (e.isConsumed || !interceptClicks) return
      lastMousePosition = Point(e.x, e.y)
    }

    override fun mouseEntered(e: MouseEvent) {
      isMouseOnPanel = true
    }

    override fun mouseExited(e: MouseEvent) {
      isMouseOnPanel = false
    }
  }
}
