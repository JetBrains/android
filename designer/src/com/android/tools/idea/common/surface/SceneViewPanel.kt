/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.surface.layout.findAllScanlines
import com.android.tools.idea.common.surface.layout.findLargerScanline
import com.android.tools.idea.common.surface.layout.findSmallerScanline
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.Rectangle
import javax.swing.JPanel

/**
 * [LayoutManager] responsible for positioning and measuring all the [SceneView] in a [DesignSurface]
 *
 * For now, the SceneViewLayoutManager does not contain actual Swing components so we do not need to layout them, just calculate the
 * size of the layout.
 * Eventually, this code will SceneViews being actual Swing components and we will not need this specialized LayoutManager.
 */
abstract class SceneViewLayoutManager: LayoutManager {
  override fun layoutContainer(parent: Container?) {}

  override fun minimumLayoutSize(parent: Container?): Dimension = Dimension(0, 0)

  override fun addLayoutComponent(name: String?, comp: Component?) {}

  override fun removeLayoutComponent(comp: Component?) {}
}

/**
 * A [SceneViewLayoutManager] for a [DesignSurface] with only one [SceneView].
 */
class SingleSceneViewLayoutManager(private val surface: DesignSurface): SceneViewLayoutManager() {
  override fun preferredLayoutSize(parent: Container?): Dimension =
    surface.sceneViews.singleOrNull()?.getScaledContentSize(null) ?: Dimension(0, 0)
}

/**
 * A [JPanel] responsible for displaying [SceneView]s from a given [DesignSurface]
 *
 * @param surface the [DesignSurface] containing the [SceneView]s
 * @param layoutManager the [SceneViewLayoutManager] responsible for positioning and measuring the [SceneView]s
 */
internal class SceneViewPanel(private val surface: DesignSurface, val layoutManager: SceneViewLayoutManager):
  JPanel(layoutManager) {
  override fun paintComponent(graphics: Graphics) {
    super.paintComponent(graphics)
    val g2d = graphics as Graphics2D

    // The visible area in the editor
    val viewportBounds: Rectangle = graphics.clipBounds

    // A Dimension used to avoid reallocating new objects just to obtain the SceneView dimensions
    val reusableDimension = Dimension()
    val sceneViewsToPaint: Collection<SceneView> = surface.sceneViews
    val horizontalTopScanLines = sceneViewsToPaint.findAllScanlines { sceneView: SceneView -> sceneView.y }
    val horizontalBottomScanLines = sceneViewsToPaint.findAllScanlines { sceneView: SceneView ->
      sceneView.y + sceneView.getScaledContentSize(reusableDimension).height
    }
    val verticalLeftScanLines = sceneViewsToPaint.findAllScanlines { sceneView: SceneView -> sceneView.x }
    val verticalRightScanLines = sceneViewsToPaint.findAllScanlines { sceneView: SceneView ->
      sceneView.x + sceneView.getScaledContentSize(reusableDimension).width
    }
    @SwingCoordinate val viewportRight = viewportBounds.x + viewportBounds.width
    @SwingCoordinate val viewportBottom = viewportBounds.y + viewportBounds.height
    val sceneViewBounds = Rectangle()
    for (sceneView in sceneViewsToPaint) {
      val sceneViewDimension = sceneView.getScaledContentSize(reusableDimension)
      @SwingCoordinate val sceneViewRight = sceneView.x + sceneViewDimension.width
      @SwingCoordinate val sceneViewBottom = sceneView.y + sceneViewDimension.height
      // This finds the maximum allowed area for the screen views to paint into. See more details in the
      // ScanlineUtils.kt documentation.
      @SwingCoordinate var minX = findSmallerScanline(verticalRightScanLines, sceneView.x, viewportBounds.x)
      @SwingCoordinate var minY = findSmallerScanline(horizontalBottomScanLines, sceneView.y, viewportBounds.y)
      @SwingCoordinate var maxX = findLargerScanline(verticalLeftScanLines,
                                                     sceneViewRight,
                                                     viewportRight)
      @SwingCoordinate var maxY = findLargerScanline(horizontalTopScanLines,
                                                     sceneViewBottom,
                                                     viewportBottom)

      // Now, (minX, minY) (maxX, maxY) describes the box that a SceneView could paint into without painting
      // on top of another SceneView render. We use this box to paint the components that are outside of the
      // rendering area.
      // However, now we need to avoid there "out of bounds" components from being on top of each other.
      // To do that, we simply find the middle point, except on the corners of the surface. For example, the
      // first SceneView on the left, does not have any other SceneView that could paint on its left side so we
      // do not need to find the middle point in those cases.
      minX = if (minX > viewportBounds.x) (minX + sceneView.x) / 2 else viewportBounds.x
      maxX = if (maxX < viewportRight) (maxX + sceneViewRight) / 2 else viewportRight
      minY = if (minY > viewportBounds.y) (minY + sceneView.y) / 2 else viewportBounds.y
      maxY = if (maxY < viewportBottom) (maxY + sceneViewBottom) / 2 else viewportBottom
      sceneViewBounds.setBounds(minX, minY, maxX - minX, maxY - minY)
      g2d.clip = sceneViewBounds
      sceneView.paint(g2d)
    }
    if (!surface.isEditable) {
      return
    }

    // Temporary overlays do not have a clipping area
    g2d.clip = viewportBounds

    // Temporary overlays:
    surface.interactionManager.layers
      .filter { it.isVisible }
      .forEach { it.paint(g2d) }
  }
}