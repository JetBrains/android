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

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.layout.findAllScanlines
import com.android.tools.idea.common.surface.layout.findLargerScanline
import com.android.tools.idea.common.surface.layout.findSmallerScanline
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.android.tools.idea.uibuilder.scene.hasValidImage
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.PositionableContentLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.getScaledContentSize
import com.intellij.openapi.Disposable
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JPanel

/**
 * A [JPanel] responsible for displaying [SceneView]s provided by the [sceneViewProvider].
 *
 * @param interactionLayersProvider A [Layer] provider that returns the additional interaction
 *   [Layer]s, if any
 * @param actionManagerProvider provides an [ActionManager]
 * @param disposable
 * @param shouldRenderErrorsPanel Returns true whether render error panels should be rendered when
 *   [SceneView] in this surface have render errors.
 * @param layoutManager the [PositionableContentLayoutManager] responsible for positioning and
 *   measuring the [SceneView]s
 */
internal class SceneViewPanel(
  private val sceneViewProvider: () -> Collection<SceneView>,
  private val interactionLayersProvider: () -> Collection<Layer>,
  private val actionManagerProvider: () -> ActionManager<*>,
  private val disposable: Disposable,
  private val shouldRenderErrorsPanel: () -> Boolean,
  layoutManager: PositionableContentLayoutManager
) : JPanel(layoutManager) {
  /**
   * Alignment for the {@link SceneView} when its size is less than the minimum size. If the size of
   * the {@link SceneView} is less than the minimum, this enum describes how to align the content
   * within the rectangle formed by the minimum size.
   */
  var sceneViewAlignment: Float = CENTER_ALIGNMENT
    set(value) {
      if (value != field) {
        field = value
        components.filterIsInstance<SceneViewPeerPanel>().forEach { it.alignmentX = value }
        repaint()
      }
    }

  /** Returns the components of this panel that are visible [PositionableContent] */
  val positionableContent: Collection<PositionableContent>
    get() =
      components
        .filterIsInstance<SceneViewPeerPanel>()
        .filter { it.isVisible }
        .map { it.positionableAdapter }
        .toList()

  /** Remove any components associated to the given model. */
  fun removeSceneViewForModel(modelToRemove: NlModel) {
    val toRemove =
      components
        .filterIsInstance<SceneViewPeerPanel>()
        .filter { it.sceneView.scene.sceneManager.model == modelToRemove }
        .toList()

    toRemove.forEach { remove(it) }
    invalidate()
  }

  @UiThread
  private fun revalidateSceneViews() {
    // Check if the SceneViews are still valid
    val designSurfaceSceneViews = sceneViewProvider()
    val currentSceneViews = findSceneViews()

    if (designSurfaceSceneViews == currentSceneViews) return // No updates

    // Invalidate the current components
    removeAll()
    designSurfaceSceneViews.forEachIndexed { index, sceneView ->
      val toolbarActions = actionManagerProvider().sceneViewContextToolbarActions
      val bottomBar = actionManagerProvider().getSceneViewBottomBar(sceneView)
      val statusIconAction = actionManagerProvider().sceneViewStatusIconAction

      // The left bar is only added for the first panel
      val leftBar = if (index == 0) actionManagerProvider().getSceneViewLeftBar(sceneView) else null
      val rightBar = actionManagerProvider().getSceneViewRightBar(sceneView)

      val errorsPanel =
        if (shouldRenderErrorsPanel())
          SceneViewErrorsPanel {
            when {
              // If the flag COMPOSE_PREVIEW_KEEP_IMAGE_ON_ERROR is enabled and  there is a valid
              // image, never display the error panel.
              sceneView.hasValidImage() && StudioFlags.COMPOSE_PREVIEW_KEEP_IMAGE_ON_ERROR.get() ->
                SceneViewErrorsPanel.Style.HIDDEN
              sceneView.hasRenderErrors() -> SceneViewErrorsPanel.Style.SOLID
              else -> SceneViewErrorsPanel.Style.HIDDEN
            }
          }
        else null

      val labelPanel = actionManagerProvider().createSceneViewLabel(sceneView)

      add(
        SceneViewPeerPanel(
            sceneView,
            labelPanel,
            statusIconAction,
            toolbarActions,
            bottomBar,
            leftBar,
            rightBar,
            errorsPanel,
          )
          .also { it.alignmentX = sceneViewAlignment }
      )
    }
  }

  override fun doLayout() {
    revalidateSceneViews()
    super.doLayout()
  }

  override fun paintComponent(graphics: Graphics) {
    super.paintComponent(graphics)
    val sceneViewPeerPanels = components.filterIsInstance<SceneViewPeerPanel>()

    if (sceneViewPeerPanels.isEmpty()) {
      return
    }

    val g2d = graphics.create() as Graphics2D
    try {
      // The visible area in the editor
      val viewportBounds: Rectangle = g2d.clipBounds

      // A Dimension used to avoid reallocating new objects just to obtain the PositionableContent
      // dimensions
      val reusableDimension = Dimension()
      val positionables: Collection<PositionableContent> =
        sceneViewPeerPanels.map { it.positionableAdapter }
      val horizontalTopScanLines = positionables.findAllScanlines { it.y }
      val horizontalBottomScanLines =
        positionables.findAllScanlines { it.y + it.getScaledContentSize(reusableDimension).height }
      val verticalLeftScanLines = positionables.findAllScanlines { it.x }
      val verticalRightScanLines =
        positionables.findAllScanlines { it.x + it.getScaledContentSize(reusableDimension).width }
      @SwingCoordinate val viewportRight = viewportBounds.x + viewportBounds.width
      @SwingCoordinate val viewportBottom = viewportBounds.y + viewportBounds.height
      val clipBounds = Rectangle()
      for (sceneViewPeerPanel in sceneViewPeerPanels) {
        val positionable = sceneViewPeerPanel.positionableAdapter
        val size = positionable.getScaledContentSize(reusableDimension)
        val renderErrorPanel =
          shouldRenderErrorsPanel() &&
            sceneViewPeerPanel.sceneView.hasRenderErrors() &&
            !sceneViewPeerPanel.sceneView.hasValidImage()

        @SwingCoordinate
        val right =
          positionable.x +
            if (renderErrorPanel) sceneViewPeerPanel.sceneViewCenterPanel.preferredSize.width
            else size.width

        @SwingCoordinate
        val bottom =
          positionable.y +
            if (renderErrorPanel) sceneViewPeerPanel.sceneViewCenterPanel.preferredSize.height
            else size.height
        // This finds the maximum allowed area for the screen views to paint into. See more details
        // in the
        // ScanlineUtils.kt documentation.
        @SwingCoordinate
        var minX = findSmallerScanline(verticalRightScanLines, positionable.x, viewportBounds.x)
        @SwingCoordinate
        var minY = findSmallerScanline(horizontalBottomScanLines, positionable.y, viewportBounds.y)
        @SwingCoordinate var maxX = findLargerScanline(verticalLeftScanLines, right, viewportRight)
        @SwingCoordinate
        var maxY = findLargerScanline(horizontalTopScanLines, bottom, viewportBottom)

        // Now, (minX, minY) (maxX, maxY) describes the box that a PositionableContent could paint
        // into without painting
        // on top of another PositionableContent render. We use this box to paint the components
        // that are outside of the
        // rendering area.
        // However, now we need to avoid there "out of bounds" components from being on top of each
        // other.
        // To do that, we simply find the middle point, except on the corners of the surface. For
        // example, the
        // first PositionableContent on the left, does not have any other PositionableContent that
        // could paint on its left side so we
        // do not need to find the middle point in those cases.
        minX = if (minX > viewportBounds.x) (minX + positionable.x) / 2 else viewportBounds.x
        maxX = if (maxX < viewportRight) (maxX + right) / 2 else viewportRight
        minY = if (minY > viewportBounds.y) (minY + positionable.y) / 2 else viewportBounds.y
        maxY = if (maxY < viewportBottom) (maxY + bottom) / 2 else viewportBottom
        clipBounds.setBounds(minX, minY, maxX - minX, maxY - minY)
        g2d.clip = clipBounds
        // Only paint the scene view if it needs to be painted, otherwise the sceneViewCenterPanel
        // will be painted instead.
        if (!renderErrorPanel) {
          sceneViewPeerPanel.sceneView.paint(g2d)
        }
      }

      val interactionLayers = interactionLayersProvider()
      if (interactionLayers.isNotEmpty()) {
        // Temporary overlays do not have a clipping area
        g2d.clip = viewportBounds

        // Temporary overlays:
        interactionLayersProvider().filter { it.isVisible }.forEach { it.paint(g2d) }
      }
    } finally {
      g2d.dispose()
    }
  }

  private fun findSceneViews(): List<SceneView> =
    components.filterIsInstance<SceneViewPeerPanel>().map { it.sceneView }.toList()

  fun findSceneViewRectangle(sceneView: SceneView): Rectangle? =
    components
      .filterIsInstance<SceneViewPeerPanel>()
      .filter { sceneView == it.sceneView }
      .map { it.bounds }
      .firstOrNull()

  fun findSceneViewRectangles(): HashMap<SceneView, Rectangle?> =
    hashMapOf(
      *components
        .filterIsInstance<SceneViewPeerPanel>()
        .distinctBy { it.sceneView }
        .map {
          it.sceneView to it.bounds.apply { location = Point(it.sceneView.x, it.sceneView.y) }
        }
        .toTypedArray()
    )

  /**
   * Find the predicted rectangle of the [sceneView] when layout manager re-layout the content with
   * the given [content] and [availableSize].
   */
  fun findMeasuredSceneViewRectangle(
    sceneView: SceneView,
    content: Collection<PositionableContent>,
    availableSize: Dimension
  ): Rectangle? {
    val panel =
      components.filterIsInstance<SceneViewPeerPanel>().firstOrNull { sceneView == it.sceneView }
        ?: return null

    val layoutManager = layout as PositionableContentLayoutManager ?: return null
    val positions =
      layoutManager.getMeasuredPositionableContentPosition(
        content,
        availableSize.width,
        availableSize.height
      )
    val position = positions[panel.positionableAdapter] ?: return null
    return panel.bounds.apply { location = position }
  }
}
