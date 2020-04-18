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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.surface.layout.findAllScanlines
import com.android.tools.idea.common.surface.layout.findLargerScanline
import com.android.tools.idea.common.surface.layout.findSmallerScanline
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.horizontal
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.geom.Dimension2D
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * [LayoutManager] responsible for positioning and measuring all the [PositionableContent] in a [DesignSurface]
 *
 * For now, the PositionableContentLayoutManager does not contain actual Swing components so we do not need to layout them, just calculate the
 * size of the layout.
 * Eventually, PositionableContent will end up being actual Swing components and we will not need this specialized LayoutManager.
 */
abstract class PositionableContentLayoutManager : LayoutManager {
  /**
   * Method called by the [PositionableContentLayoutManager] to make sure that the layout of the [PositionableContent]s
   * to ask them to be laid out within the [SceneViewPanel].
   */
  abstract fun layoutContent(content: Collection<PositionableContent>)

  final override fun layoutContainer(parent: Container) {
    val sceneViewPeerPanels = parent.components.filterIsInstance<SceneViewPeerPanel>()

    // We lay out the [SceneView]s first, so we have the actual sizes available for setting the
    // bounds of the Swing components.
    layoutContent(sceneViewPeerPanels.map { it.positionableAdapter })

    // Now position all the wrapper panels to match the position of the SceneViews
    sceneViewPeerPanels
      .forEach {
        val peerPreferredSize = it.preferredSize
        it.setBounds(it.positionableAdapter.x - it.positionableAdapter.margin.left,
                     it.positionableAdapter.y - it.positionableAdapter.margin.top,
                     peerPreferredSize.width,
                     peerPreferredSize.height)
      }
  }

  override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, 0)
  override fun addLayoutComponent(name: String?, comp: Component?) {}
  override fun removeLayoutComponent(comp: Component?) {}
}

/**
 * A [PositionableContentLayoutManager] for a [DesignSurface] with only one [PositionableContent].
 */
class SinglePositionableContentLayoutManager : PositionableContentLayoutManager() {
  override fun layoutContent(content: Collection<PositionableContent>) {
    content.singleOrNull()?.setLocation(0, 0)
  }

  override fun preferredLayoutSize(parent: Container): Dimension =
    parent.components
      .filterIsInstance<SceneViewPeerPanel>()
      .singleOrNull()
      ?.positionableAdapter
      ?.getScaledContentSize(null)
    ?: Dimension(0, 0)
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
@VisibleForTesting
class SceneViewPeerPanel(val sceneView: SceneView,
                         private val sceneViewToolbar: JComponent?) : JPanel() {
  /**
   * Contains cached layout data that can be used by this panel to verify when it's been invalidated
   * without having to explicitly call [revalidate]
   */
  private var layoutData = LayoutData.fromSceneView(sceneView)

  /**
   * This label displays the [SceneView] model if there is any
   */
  private val modelNameLabel = JLabel().apply {
    maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
  }

  val positionableAdapter = object : PositionableContent() {
    override val x: Int get() = sceneView.x
    override val y: Int get() = sceneView.y
    override val margin: Insets
      get() {
        // If there is no content, or the content is smaller than the minimum size, pad the margins to occupy the empty space
        val contentSize = if (sceneView.hasContent()) sceneView.getScaledContentSize(null) else JBUI.emptySize()
        return if (contentSize.width < minimumSize.width &&
                   contentSize.height < minimumSize.height) {
          val hSpace = (minimumSize.width - contentSize.width) / 2
          val vSpace = (minimumSize.height - contentSize.height) / 2
          val originalMargin = sceneView.margin
          Insets(originalMargin.top + vSpace,
                 originalMargin.left + hSpace,
                 originalMargin.bottom + vSpace,
                 originalMargin.right + hSpace)
        }
        else {
          sceneView.margin
        }
      }

    override fun getContentSize(dimension: Dimension?): Dimension = sceneView.getContentSize(dimension)
    override fun getScaledContentSize(dimension: Dimension?): Dimension {
      val outputDimension = dimension ?: Dimension()

      val contentSize = getContentSize(outputDimension)
      val scale: Double = sceneView.scale

      outputDimension.setSize((scale * contentSize.width).toInt(), (scale * contentSize.height).toInt())
      return outputDimension
    }

    override fun setLocation(x: Int, y: Int) = sceneView.setLocation(x, y)
  }

  /**
   * This panel wraps both the label and the toolbar and puts them left aligned (label) and right
   * aligned (the toolbar).
   */
  @VisibleForTesting
  val sceneViewTopPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(modelNameLabel, BorderLayout.CENTER)
    if (sceneViewToolbar != null) {
      add(sceneViewToolbar, BorderLayout.LINE_END)
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
      modelNameLabel.toolTipText = ""
      sceneViewTopPanel.isVisible = false
    }
    else {
      modelNameLabel.text = layoutData.modelName
      modelNameLabel.toolTipText = layoutData.modelName
      sceneViewTopPanel.setBounds(0, 0,
                                  width + insets.horizontal,
                                  positionableAdapter.margin.top - 2 + positionableAdapter.margin.bottom)
      sceneViewTopPanel.isVisible = true
    }

    super.doLayout()
  }

  /** [Dimension] used to avoid extra allocations calculating [getPreferredSize] */
  private val cachedContentSize = Dimension()
  override fun getPreferredSize(): Dimension = positionableAdapter.getScaledContentSize(cachedContentSize).also {
    it.width = it.width + positionableAdapter.margin.left + positionableAdapter.margin.right
    it.height = it.height + positionableAdapter.margin.top + positionableAdapter.margin.bottom
  }

  override fun getMinimumSize(): Dimension =
    Dimension(sceneViewTopPanel.minimumSize.width, sceneViewTopPanel.minimumSize.height + JBUI.scale(20))
}

/**
 * A [JPanel] responsible for displaying [SceneView]s. The [SceneView]s need to be explicitly added by the surface by calling
 * [addSceneView] and removed by calling [removeSceneView]. Only [SceneView]s added by calling thosemethods will be rendered by this panel.
 *
 * @param interactionLayersProvider A [Layer] provider that returns the additional interaction [Layer]s, if any
 * @param layoutManager the [PositionableContentLayoutManager] responsible for positioning and measuring the [SceneView]s
 */
internal class SceneViewPanel(private val interactionLayersProvider: () -> List<Layer>,
                              val layoutManager: PositionableContentLayoutManager) :
  JPanel(layoutManager) {

  /**
   * Returns the components of this panel that are [PositionableContent]
   */
  val positionableContent: Collection<PositionableContent>
    get() = components.filterIsInstance<SceneViewPeerPanel>()
      .map { it.positionableAdapter }
      .toList()

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

      // A Dimension used to avoid reallocating new objects just to obtain the PositionableContent dimensions
      val reusableDimension = Dimension()
      val positionables: Collection<PositionableContent> = sceneViewPeerPanels.map { it.positionableAdapter }
      val horizontalTopScanLines = positionables.findAllScanlines { it.y }
      val horizontalBottomScanLines = positionables.findAllScanlines { it.y + it.getScaledContentSize(reusableDimension).height }
      val verticalLeftScanLines = positionables.findAllScanlines { it.x }
      val verticalRightScanLines = positionables.findAllScanlines { it.x + it.getScaledContentSize(reusableDimension).width }
      @SwingCoordinate val viewportRight = viewportBounds.x + viewportBounds.width
      @SwingCoordinate val viewportBottom = viewportBounds.y + viewportBounds.height
      val clipBounds = Rectangle()
      for (sceneViewPeerPanel in sceneViewPeerPanels) {
        val positionable = sceneViewPeerPanel.positionableAdapter
        val size = positionable.getScaledContentSize(reusableDimension)
        @SwingCoordinate val right = positionable.x + size.width
        @SwingCoordinate val bottom = positionable.y + size.height
        // This finds the maximum allowed area for the screen views to paint into. See more details in the
        // ScanlineUtils.kt documentation.
        @SwingCoordinate var minX = findSmallerScanline(verticalRightScanLines, positionable.x, viewportBounds.x)
        @SwingCoordinate var minY = findSmallerScanline(horizontalBottomScanLines, positionable.y, viewportBounds.y)
        @SwingCoordinate var maxX = findLargerScanline(verticalLeftScanLines,
                                                       right,
                                                       viewportRight)
        @SwingCoordinate var maxY = findLargerScanline(horizontalTopScanLines,
                                                       bottom,
                                                       viewportBottom)

        // Now, (minX, minY) (maxX, maxY) describes the box that a PositionableContent could paint into without painting
        // on top of another PositionableContent render. We use this box to paint the components that are outside of the
        // rendering area.
        // However, now we need to avoid there "out of bounds" components from being on top of each other.
        // To do that, we simply find the middle point, except on the corners of the surface. For example, the
        // first PositionableContent on the left, does not have any other PositionableContent that could paint on its left side so we
        // do not need to find the middle point in those cases.
        minX = if (minX > viewportBounds.x) (minX + positionable.x) / 2 else viewportBounds.x
        maxX = if (maxX < viewportRight) (maxX + right) / 2 else viewportRight
        minY = if (minY > viewportBounds.y) (minY + positionable.y) / 2 else viewportBounds.y
        maxY = if (maxY < viewportBottom) (maxY + bottom) / 2 else viewportBottom
        clipBounds.setBounds(minX, minY, maxX - minX, maxY - minY)
        g2d.clip = clipBounds
        sceneViewPeerPanel.sceneView.paint(g2d)
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
  @UiThread
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
  @AnyThread
  fun removeSceneView(sceneView: SceneView) {
    components
      .filterIsInstance<SceneViewPeerPanel>()
      .filter { sceneView == it.sceneView }
      .forEach { remove(it) }
  }
}