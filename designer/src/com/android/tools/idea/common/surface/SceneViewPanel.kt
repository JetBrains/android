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
import com.android.tools.adtui.ui.ClickableLabel
import com.android.tools.idea.common.surface.layout.findAllScanlines
import com.android.tools.idea.common.surface.layout.findLargerScanline
import com.android.tools.idea.common.surface.layout.findSmallerScanline
import com.android.tools.idea.flags.StudioFlags
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.Rectangle
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private fun Array<Component>.getSceneViewsFromWrappers(): Collection<SceneView> =
  filterIsInstance<SceneViewPeerPanel>().map { it.sceneView }

/**
 * [LayoutManager] responsible for positioning and measuring all the [SceneView] in a [DesignSurface]
 *
 * For now, the SceneViewLayoutManager does not contain actual Swing components so we do not need to layout them, just calculate the
 * size of the layout.
 * Eventually, SceneViews will end up being actual Swing components and we will not need this specialized LayoutManager.
 */
abstract class SceneViewLayoutManager: LayoutManager {
  /**
   * Method called by the [SceneViewLayoutManager] to make sure that the layout of the [SceneView]s
   * to ask them to be laid out within the [SceneViewPanel].
   */
  abstract fun layoutSceneViews(sceneViews: Collection<SceneView>)

  final override fun layoutContainer(parent: Container) {
    val sceneViews = parent.components.getSceneViewsFromWrappers()

    // We lay out the [SceneView]s first, so we have the actual sizes available for setting the
    // bounds of the Swing components.
    layoutSceneViews(sceneViews)

    // Now position all the wrapper panels to match the position of the SceneViews
    val size = Dimension()
    parent.components
      .filterIsInstance<SceneViewPeerPanel>()
      .forEach {
        val sceneView = it.sceneView
        sceneView.getScaledContentSize(size)
        it.setBounds(sceneView.x - sceneView.margin.left,
                     sceneView.y - sceneView.margin.top,
                     size.width + sceneView.margin.left + sceneView.margin.right,
                     size.height + sceneView.margin.top + sceneView.margin.bottom)
      }
  }

  override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, 0)
  override fun addLayoutComponent(name: String?, comp: Component?) {}
  override fun removeLayoutComponent(comp: Component?) {}
}

/**
 * A [SceneViewLayoutManager] for a [DesignSurface] with only one [SceneView].
 */
class SingleSceneViewLayoutManager : SceneViewLayoutManager() {
  override fun layoutSceneViews(sceneViews: Collection<SceneView>) {
    sceneViews.singleOrNull()?.setLocation(0, 0)
  }

  override fun preferredLayoutSize(parent: Container): Dimension =
    parent.components.getSceneViewsFromWrappers().singleOrNull()?.getScaledContentSize(null) ?: Dimension(0, 0)
}

private data class LayoutData private constructor(
  val scale: Double,
  val modelName: String?,
  val x: Int,
  val y: Int,
  val scaledSize: Dimension) {

  // Used to avoid extra allocations in isValidFor calls
  private val cachedDimension = Dimension()

  /**
   * Returns whether this [LayoutData] is still valid (has not changed) for the given [SceneView]
   */
  fun isValidFor(sceneView: SceneView): Boolean =
    scale == sceneView.scale &&
    x == sceneView.x && y == sceneView.y &&
    modelName == sceneView.scene.sceneManager.model.modelDisplayName &&
    scaledSize == sceneView.getScaledContentSize(cachedDimension)

  companion object {
    fun fromSceneView(sceneView: SceneView): LayoutData =
      LayoutData(
        sceneView.scale,
        sceneView.scene.sceneManager.model.modelDisplayName,
        sceneView.x,
        sceneView.y,
        sceneView.scaledContentSize)
  }
}

/**
 * A Swing component associated to the given [SceneView]. There will be one of this components in the [DesignSurface]
 * per every [SceneView] available. This panel will be positioned on the coordinates of the [SceneView] and can be
 * used to paint Swing elements on top of the [SceneView].
 */
private class SceneViewPeerPanel(val sceneView: SceneView, sceneViewToolbar: JComponent?) : JPanel() {
  /**
   * Contains cached layout data that can be used by this panel to verify when it's been invalidated
   * without having to explicitly call [revalidate]
   */
  private var layoutData = LayoutData.fromSceneView(sceneView)

  /**
   * This label displays the [SceneView] model if there is any
   */
  private val modelNameLabel = JLabel().apply {
    minimumSize = Dimension(0, 0)
    maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
  }

  /**
   * This panel wraps both the label and the toolbar and puts them left aligned (label) and right
   * aligned (the toolbar).
   */
  private val sceneViewTopPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.LINE_AXIS)
    isOpaque = false
    add(modelNameLabel)
    if (sceneViewToolbar != null) {
      add(Box.createHorizontalGlue())
      add(sceneViewToolbar)
    }
  }

  init {
    isOpaque = false
    layout = null

    add(sceneViewTopPanel)
  }

  override fun isValid(): Boolean {
    return super.isValid() && layoutData.isValidFor(sceneView)
  }

  override fun doLayout() {
    layoutData = LayoutData.fromSceneView(sceneView)

    // If there is a model name, we manually assign the content of the modelNameLabel and position it here.
    // Once this panel gets more functionality, we will need the use of a layout manager. For now, we just lay out the component manually.
    if (layoutData.modelName == null) {
      modelNameLabel.text = ""
      sceneViewTopPanel.isVisible = false
    }
    else {
      modelNameLabel.text = layoutData.modelName
      sceneViewTopPanel.setBounds(0, 0, width, sceneView.margin.top - 2)
      sceneViewTopPanel.isVisible = true
    }

    super.doLayout()
  }
}

/**
 * A [JPanel] responsible for displaying [SceneView]s. The [SceneView]s need to be explicitly added by the surface by calling
 * [addSceneView] and removed by calling [removeSceneView]. Only [SceneView]s added by calling thosemethods will be rendered by this panel.
 *
 * @param interactionLayersProvider A [Layer] provider that returns the additional interaction [Layer]s, if any
 * @param layoutManager the [SceneViewLayoutManager] responsible for positioning and measuring the [SceneView]s
 */
internal class SceneViewPanel(private val interactionLayersProvider: () -> List<Layer>, val layoutManager: SceneViewLayoutManager):
  JPanel(layoutManager) {
  override fun paintComponent(graphics: Graphics) {
    super.paintComponent(graphics)
    val g2d = graphics.create() as Graphics2D

    try {
      // The visible area in the editor
      val viewportBounds: Rectangle = graphics.clipBounds

      // A Dimension used to avoid reallocating new objects just to obtain the SceneView dimensions
      val reusableDimension = Dimension()
      val sceneViewsToPaint: Collection<SceneView> = components.getSceneViewsFromWrappers()
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

      val interactionLayers = interactionLayersProvider()
      if (interactionLayers.isNotEmpty()) {
        // Temporary overlays do not have a clipping area
        g2d.clip = viewportBounds

        // Temporary overlays:
        interactionLayersProvider()
          .filter { it.isVisible }
          .forEach { it.paint(g2d) }
      }
    }
    finally {
      g2d.dispose()
    }
  }

  /**
   * Adds the given [SceneView] to this panel if it was not part of the panel already.
   */
  fun addSceneView(sceneView: SceneView) {
    val alreadyAdded = components
      .filterIsInstance<SceneViewPeerPanel>()
      .any { sceneView == it.sceneView }

    if (!alreadyAdded) {
      val toolbar = if (StudioFlags.NELE_SCENEVIEW_TOP_TOOLBAR.get()) {
        sceneView.surface.actionManager.getSceneViewContextToolbar(sceneView)
      }
      else {
        null
      }

      add(SceneViewPeerPanel(sceneView, toolbar))
    }
  }

  /**
   * Removes the given [SceneView] from the panel.
   */
  fun removeSceneView(sceneView: SceneView) {
    components
      .filterIsInstance<SceneViewPeerPanel>()
      .filter { sceneView == it.sceneView }
      .forEach { remove(it) }
  }
}