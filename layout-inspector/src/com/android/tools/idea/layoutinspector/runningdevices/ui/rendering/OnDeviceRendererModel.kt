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
import com.android.tools.idea.layoutinspector.common.ephemeralFlow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.InputEvent
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

/**
 * Model for a renderer that delegates the actual rendering to the device. Sends rendering
 * instructions to the device each time the [renderModel] changes and handles input events from the
 * device. There should be at most one model for each device, to avoid sending and receiving
 * duplicated events.
 */
class OnDeviceRendererModel(
  disposable: Disposable,
  scope: CoroutineScope,
  val renderModel: EmbeddedRendererModel,
) : Disposable {
  companion object {
    private val logger = Logger.getInstance(OnDeviceRendererModel::class.java)
  }

  /**
   * Test-only constructor to avoid having to deal with connecting a fake client in tests. This
   * model is immediately connected to [client].
   */
  @TestOnly
  constructor(
    disposable: Disposable,
    scope: CoroutineScope,
    renderModel: EmbeddedRendererModel,
    client: OnDeviceRenderingClient,
  ) : this(disposable, scope, renderModel) {
    childScope.launch { onNewClientConnected(client) }
  }

  private val childScope = scope.createChildScope(parentDisposable = this)

  val inspectorModel
    get() = renderModel.inspectorModel

  val interceptClicks
    get() = renderModel.interceptClicks

  private val _rightClick = ephemeralFlow<InputEvent>()
  val rightClick = _rightClick.asSharedFlow()

  var ignoreHoverEvents = false

  /** Job used to control the on-device rendering on a client */
  @VisibleForTesting var onNewClientJob: Job? = null

  private val connectionListener =
    InspectorModel.ConnectionListener { newClient ->
      if (newClient.isConnected && newClient is AppInspectionInspectorClient) {
        logger.info("Setting up rendering on new client")

        val viewInspector =
          checkNotNull(newClient.viewInspector) { "View Inspector is null on a connected client." }

        // When a new client is connected, set up on-device rendering events.
        onNewClientJob?.cancel()
        onNewClientJob = childScope.launch { onNewClientConnected(viewInspector.onDeviceRendering) }
      } else {
        // Each time the connection changes the current instance of client is stale.
        onNewClientJob?.cancel()
      }
    }

  init {
    Disposer.register(disposable, this)
    renderModel.inspectorModel.addConnectionListener(connectionListener)
  }

  override fun dispose() {
    renderModel.inspectorModel.removeConnectionListener(connectionListener)
  }

  /** Set up the new client and start sending and receiving events. */
  private suspend fun onNewClientConnected(client: OnDeviceRenderingClient) = coroutineScope {
    client.enableOnDeviceRendering(true)

    launch {
      renderModel.interceptClicks.collect { interceptClicks ->
        client.interceptTouchEvents(interceptClicks)
      }
    }

    launch {
      renderModel.selectedNode.collect { selectedNode -> client.drawSelectedNode(selectedNode) }
    }

    launch {
      renderModel.hoveredNode.collect { hoveredNode -> client.drawHoveredNode(hoveredNode) }
    }

    launch {
      renderModel.visibleNodes.collect { visibleNodes -> client.drawVisibleNodes(visibleNodes) }
    }

    launch {
      renderModel.recomposingNodes.collect { recomposingNodes ->
        client.drawRecomposingNodes(recomposingNodes)
      }
    }

    launch { renderModel.overlay.collect { overlay -> client.drawOverlay(overlay) } }

    launch { renderModel.overlayAlpha.collect { alpha -> client.setOverlayAlpha(alpha) } }

    launch {
      client.selectionEvents.filterNotNull().collect { event ->
        if (interceptClicks.value) {
          renderModel.selectNode(event.x.toDouble(), event.y.toDouble(), event.rootId)
        }
      }
    }

    launch {
      client.hoverEvents.filterNotNull().collect { event ->
        if (interceptClicks.value && !ignoreHoverEvents) {
          renderModel.hoverNode(event.x.toDouble(), event.y.toDouble(), event.rootId)
        }
      }
    }

    launch {
      client.rightClickEvents.filterNotNull().collect { event ->
        if (!interceptClicks.value) {
          return@collect
        }

        _rightClick.tryEmit(event)
      }
    }

    launch {
      client.doubleClickEvents.filterNotNull().collect { event ->
        if (interceptClicks.value) {
          renderModel.doubleClickNode(event.x.toDouble(), event.y.toDouble(), event.rootId)
        }
      }
    }
  }
}
