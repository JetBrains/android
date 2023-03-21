/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices


import com.android.tools.idea.layoutinspector.ui.RenderLogic
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.android.tools.idea.streaming.AbstractDisplayView

/**
 * Manager class to hook into Running Device's [AbstractDisplayView].
 * This class sets things up when Layout Inspector needs to start interacting with [AbstractDisplayView] (eg: rendering contributors,
 * click listeners etc.) and cleans things up when Layout Inspector is disabled.
 */
class DisplayViewManager(
  private val renderModel: RenderModel,
  renderLogic: RenderLogic,
  private val displayView: AbstractDisplayView
) {

  private val layoutInspectorRenderer = LayoutInspectorRenderer(
    renderLogic,
    renderModel,
    displayView
  )

  private val repaintDisplayView = {
    displayView.revalidate()
    displayView.repaint()
  }

  private val decorationPainter = AbstractDisplayView.DecorationPainter { graphics, displayRectangle, _, _ ->
    layoutInspectorRenderer.paint(graphics, displayRectangle)
  }

  /** Start rendering Layout Inspector in [AbstractDisplayView] */
  fun startRendering() {
    // re-render each time Layout Inspector model changes
    renderModel.modificationListeners.add(repaintDisplayView)

    // add a renderer to render the Layout Inspector view bounds
    displayView.addDecorationRenderer(decorationPainter)
  }

  /** Stop rendering Layout Inspector in [AbstractDisplayView] */
  fun stopRendering() {
    renderModel.modificationListeners.remove(repaintDisplayView)
    displayView.removeDecorationRenderer(decorationPainter)
  }
}