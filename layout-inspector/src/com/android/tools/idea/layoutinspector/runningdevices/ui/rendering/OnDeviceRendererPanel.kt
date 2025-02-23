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

import com.android.adblib.utils.createChildScope
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Renderer that delegates the actual rendering to the device. Sends rendering instructions to the
 * device each time the model changes. Handles input events from the device.
 */
class OnDeviceRendererPanel(
  disposable: Disposable,
  scope: CoroutineScope,
  private val client: OnDeviceRenderingClient,
  private val renderModel: OnDeviceRendererModel,
) : LayoutInspectorRenderer() {

  private val childScope = scope.createChildScope()

  override var interceptClicks
    get() = renderModel.interceptClicks.value
    set(value) = renderModel.setInterceptClicks(value)

  init {
    Disposer.register(disposable, this)
    isOpaque = false

    childScope.launch { client.enableOnDeviceRendering(true) }

    childScope.launch {
      renderModel.interceptClicks.collect { interceptClicks ->
        client.interceptTouchEvents(interceptClicks)
      }
    }

    childScope.launch {
      renderModel.selectedNode.collect { selectedNode -> client.drawSelectedNode(selectedNode) }
    }

    childScope.launch {
      renderModel.hoveredNode.collect { hoveredNode -> client.drawHoveredNode(hoveredNode) }
    }

    childScope.launch {
      renderModel.visibleNodes.collect { visibleNodes -> client.drawVisibleNodes(visibleNodes) }
    }

    childScope.launch {
      client.selectionEvents.filterNotNull().collect { event ->
        if (interceptClicks) {
          renderModel.selectNode(event.x.toDouble(), event.y.toDouble(), event.rootId)
        }
      }
    }

    childScope.launch {
      client.hoverEvents.filterNotNull().collect { event ->
        if (interceptClicks) {
          renderModel.hoverNode(event.x.toDouble(), event.y.toDouble(), event.rootId)
        }
      }
    }
  }

  override fun dispose() {
    childScope.cancel()
  }
}
