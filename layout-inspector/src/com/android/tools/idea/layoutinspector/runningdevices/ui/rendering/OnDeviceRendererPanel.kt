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
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.DrawInstruction
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.android.tools.idea.layoutinspector.ui.RenderModel
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
  private val renderModel: RenderModel,
) : LayoutInspectorRenderer() {

  private val childScope = scope.createChildScope()

  override var interceptClicks = false
    set(value) {
      if (field == value) {
        return
      }

      field = value
      childScope.launch { client.interceptTouchEvents(value) }
    }

  private val selectionListener =
    object : InspectorModel.SelectionListener {
      override fun onSelection(oldNode: ViewNode?, newNode: ViewNode?, origin: SelectionOrigin) {
        val drawInstruction =
          newNode?.let {
            val rootView =
              renderModel.model.rootFor(newNode)
                ?: throw IllegalStateException("No root view found for view node ${newNode.viewId}")
            DrawInstruction(rootViewId = rootView.drawId, bounds = newNode.layoutBounds)
          }

        childScope.launch { client.drawSelectedNode(drawInstruction) }
      }
    }

  init {
    Disposer.register(disposable, this)
    isOpaque = false

    childScope.launch { client.enableOnDeviceRendering(true) }

    childScope.launch {
      client.touchEvents.filterNotNull().collect { touchEvent ->
        if (interceptClicks) {
          renderModel.selectView(touchEvent.x.toDouble(), touchEvent.y.toDouble())
        }
      }
    }

    renderModel.model.addSelectionListener(selectionListener)
  }

  override fun dispose() {
    renderModel.model.removeSelectionListener(selectionListener)
    childScope.cancel()
  }
}
