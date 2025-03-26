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
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

/**
 * Renderer that delegates the actual rendering to the device. Sends rendering instructions to the
 * device each time the model changes. Handles input events from the device. This class acts like a
 * manager for [OnDeviceRendererPanelImpl], making sure the current instance on
 * [onDeviceRendererPanel] is never stale.
 */
class OnDeviceRendererPanel(
  disposable: Disposable,
  private val scope: CoroutineScope,
  private val renderModel: OnDeviceRendererModel,
  private val enableSendRightClicksToDevice: (enable: Boolean) -> Unit,
) : LayoutInspectorRenderer() {
  companion object {
    private val logger = Logger.getInstance(RootPanelRenderer::class.java)
  }

  private var onDeviceRendererPanel: OnDeviceRendererPanelImpl? = null
    set(value) {
      if (field == value) {
        // No need to replace if it's the same instance.
        return
      }
      logger.info("Setting up new renderer.")

      field?.let {
        // Remove and dispose the old renderer
        remove(it)
        Disposer.dispose(it)
      }

      field = value

      // Add the new renderer
      value?.let { addToCenter(it) }
    }

  override var interceptClicks: Boolean
    get() = renderModel.interceptClicks.value
    set(value) {
      renderModel.setInterceptClicks(value)
    }

  private val connectionListener =
    object : InspectorModel.ConnectionListener {
      override fun onConnectionChanged(newClient: InspectorClient) {
        onDeviceRendererPanel =
          if (newClient.isConnected && newClient is AppInspectionInspectorClient) {
            val viewInspector =
              checkNotNull(newClient.viewInspector) {
                "View Inspector is null on a connected client."
              }
            OnDeviceRendererPanelImpl(
              disposable = this@OnDeviceRendererPanel,
              scope = scope,
              client = viewInspector.onDeviceRendering,
              renderModel = renderModel,
              enableSendRightClicksToDevice = enableSendRightClicksToDevice,
            )
          } else {
            // Each time the connection changes the on device renderer holds a stale instance of
            // OnDeviceRenderingClient.
            null
          }
      }
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

    renderModel.inspectorModel.addConnectionListener(connectionListener)
  }

  override fun dispose() {
    renderModel.inspectorModel.removeConnectionListener(connectionListener)
  }
}

/**
 * Class that implements the actual on-device rendering. This class holds an instance of
 * [OnDeviceRenderingClient] which goes stale once the client is disconnected.
 */
@VisibleForTesting
class OnDeviceRendererPanelImpl(
  disposable: Disposable,
  scope: CoroutineScope,
  private val client: OnDeviceRenderingClient,
  private val renderModel: OnDeviceRendererModel,
  private val enableSendRightClicksToDevice: (enable: Boolean) -> Unit,
) : BorderLayoutPanel(), Disposable {

  private val childScope = scope.createChildScope()
  private val interceptClicks
    get() = renderModel.interceptClicks.value

  private var lastMousePosition: Point? = null

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
    MouseListener().also { addMouseMotionListener(it) }

    childScope.launch { client.enableOnDeviceRendering(true) }

    childScope.launch {
      renderModel.interceptClicks.collect { interceptClicks ->
        enableSendRightClicksToDevice(interceptClicks)
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
      renderModel.recomposingNodes.collect { recomposingNodes ->
        client.drawRecomposingNodes(recomposingNodes)
      }
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

    childScope.launch {
      client.rightClickEvents.filterNotNull().collect { event ->
        if (!interceptClicks) {
          return@collect
        }
        val views = renderModel.findNodesAt(event.x.toDouble(), event.y.toDouble(), event.rootId)
        // There should always be a lastMousePosition available, if for some reason it's missing,
        // show the popup in them middle of the panel.
        val rightClickCoordinates = lastMousePosition ?: Point(width / 2, height / 2)
        withContext(Dispatchers.EDT) {
          showViewContextMenu(
            views = views.toList(),
            inspectorModel = renderModel.inspectorModel,
            source = this@OnDeviceRendererPanelImpl,
            x = rightClickCoordinates.x,
            y = rightClickCoordinates.y,
          )
        }
      }
    }
  }

  override fun dispose() {
    childScope.cancel()
  }

  private inner class MouseListener : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      if (e.isConsumed || !interceptClicks) return
      lastMousePosition = Point(e.x, e.y)
    }
  }
}
