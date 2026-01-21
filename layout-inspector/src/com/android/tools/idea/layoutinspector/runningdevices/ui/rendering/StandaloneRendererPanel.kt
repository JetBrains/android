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

import com.intellij.openapi.Disposable
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import kotlinx.coroutines.CoroutineScope

/** Panel responsible for rendering Layout Inspector UI for standalone Layout Inspector V2. */
class StandaloneRendererPanel(
  disposable: Disposable,
  scope: CoroutineScope,
  renderModel: EmbeddedRendererModel,
) : AbstractStudioRendererPanel(disposable, scope, renderModel) {

  /** The rectangle delimiting the drawing area */
  private val drawRectangleProvider: () -> Rectangle = {
    renderModel.inspectorModel.root.layoutBounds
  }

  private val scaleProvider: () -> Double = { renderModel.renderSettings.scaleFraction }

  override val interceptClicks = true

  override fun getRenderTransform(): AffineTransform {
    return AffineTransform().apply {
      val drawRectangle = drawRectangleProvider()
      val scale = scaleProvider()

      // Translate to center of the panel
      translate(size.width / 2.0, size.height / 2.0)
      scale(scale, scale)
      // Center the rendering
      translate(-drawRectangle.width / 2.0, -drawRectangle.height / 2.0)
    }
  }

  override fun getOverlayBounds(transform: AffineTransform): Rectangle {
    return drawRectangleProvider()
  }

  override fun getPreferredSize(): Dimension {
    val renderBounds = renderModel.inspectorModel.root.layoutBounds
    val scale = scaleProvider()

    val contentWidth = (renderBounds.width * scale).toInt()
    val contentHeight = (renderBounds.height * scale).toInt()

    // This allows the container panel to inject margins via setBorder()
    val insets = insets

    // Change the size of the panel according to the size of the rendered bounds. This is useful to
    // trigger scrollbars when this panel is wrapped in a scrollable panel.
    return Dimension(
      contentWidth + insets.left + insets.right,
      contentHeight + insets.top + insets.bottom,
    )
  }
}
