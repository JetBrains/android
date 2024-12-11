/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.adtui.util.makeNavigable
import com.android.tools.adtui.util.scaled
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneEx
import com.intellij.util.ui.AbstractLayoutManager
import com.intellij.util.ui.Animator
import com.intellij.util.ui.GraphicsUtil.disableAAPainting
import com.intellij.util.ui.GraphicsUtil.setupAAPainting
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar.SEPARATOR_COLOR
import com.intellij.util.ui.components.BorderLayoutPanel
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Shape
import java.awt.Transparency
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants.HORIZONTAL
import javax.swing.SwingConstants.VERTICAL
import kotlin.math.max
import kotlin.math.min

/**
 * A container a floating toolbars that may change their appearance depending on current mouse
 * cursor position. When the mouse cursor is over a toolbar, all toolbars become active, otherwise
 * they become inactive. The toolbars may become semi-transparent and/or shrink when inactive.
 */
internal class FloatingToolbarContainer(horizontal: Boolean, private val inactiveAlpha: Double = 1.0) : JPanel() {

  @Orientation
  private val orientation = if (horizontal) HORIZONTAL else VERTICAL
  private val actionToolbars = mutableListOf<ActionToolbar>()

  private var disposable: Disposable? = null
  private var listeningToMouseEvents = false
  private var activationAnimator: Animator? = null
  private var deactivationAnimator: Animator? = null
  private var pendingDeactivation = false

  /** Zero means inactive, one means active. */
  @VisibleForTesting
  internal var activationFactor: Double = 0.0
    private set(value) {
      if (field != value) {
        field = value
        if (hasCollapsibleToolbar) {
          expansionFactor = activationFactor
        }
        alpha = ((inactiveAlpha + (ACTIVE_ALPHA - inactiveAlpha) * value)).coerceIn(inactiveAlpha, ACTIVE_ALPHA)
      }
    }
  private var expansionFactor: Double = 1.0
    set(value) {
      if (field != value) {
        field = value
        revalidate()
      }
    }
  private var alpha: Double = inactiveAlpha
    set(value) {
      if (field != value) {
        field = value
        for (child in components) {
          if (child is ToolbarPanel) {
            child.alpha = alpha.toFloat()
          }
        }
        repaint()
      }
    }
  private var hasCollapsibleToolbar = false
    set(value) {
      if (field != value) {
        field = value
        if (value) {
          expansionFactor = activationFactor
          disposable?.let { setUpMouseListener(it) }
        }
      }
    }

  init {
    require(inactiveAlpha in 0.0..1.0)
    isOpaque = false
    layout = Layout()
  }

  /**
   * Adds a floating toolbar. If the toolbar is collapsible, it shrinks to the size of one button
   * when inactive. The button that remains visible when the toolbar is shrunk is either the first
   * selected toggle button or the first button of the toolbar.
   *
   * If the toolbar being added is not collapsible but there is at least one collapsible toolbar,
   * the non-collapsible toolbar will become hidden when the collapsible toolbars are shrunk.
   */
  fun addToolbar(@NonNls place: String, actionGroup: ActionGroup, collapsible: Boolean) {
    val actionToolbar = ActionManager.getInstance().createActionToolbar(place, actionGroup, orientation == HORIZONTAL).apply {
      configureToolbar()
    }
    actionToolbars.add(actionToolbar)
    val toolbarPanel = ToolbarPanel(actionToolbar, collapsible)
    toolbarPanel.alpha = alpha.toFloat()
    add(toolbarPanel)
    if (collapsible) {
      hasCollapsibleToolbar = true
    }
  }

  fun setTargetComponent(component: JComponent) {
    for (toolbar in actionToolbars) {
      toolbar.targetComponent = component
    }
  }

  private fun setUpMouseListener(disposable: Disposable) {
    if (listeningToMouseEvents) {
      return // Already set up.
    }

    val mouseListener = object : MouseAdapter() {

      override fun mouseEntered(event: MouseEvent) {
        mouseMoved(event)
      }

      override fun mouseExited(event: MouseEvent) {
        mouseMoved(event)
      }

      override fun mouseMoved(event: MouseEvent) {
        controlActivation(event)
      }
    }
    val glass = IdeGlassPaneUtil.find(this) as IdeGlassPaneEx
    glass.addMousePreprocessor(mouseListener, disposable)
    glass.addMouseMotionPreprocessor(mouseListener, disposable)
    listeningToMouseEvents = true
  }

  private fun controlActivation(event: MouseEvent) {
    val mouseLocation = event.locationOnScreen - locationOnScreen
    if (mouseLocation.x >= 0 && mouseLocation.y >= 0 && mouseLocation.x < width && mouseLocation.y < height &&
        componentCount != 0 && mouseLocation[orientation] >= getComponent(0).location[orientation]) {
      triggerActivation()
    }
    else {
      triggerDeactivation()
    }
  }

  private fun triggerActivation() {
    pendingDeactivation = false
    deactivationAnimator?.dispose()
    if (activationAnimator == null && activationFactor < 1.0) {
      activationAnimator = ActivationAnimator(ACTIVATION_ANIMATION_DURATION_MILLIS.scaled(1 - activationFactor)).apply { resume() }
    }
  }

  private fun triggerDeactivation() {
    if (activationAnimator == null) {
      if (activationFactor > 0.0 && deactivationAnimator == null) {
        deactivationAnimator = DeactivationAnimator(COLLAPSE_ANIMATION_DURATION_MILLIS).apply { resume() }
      }
    }
    else {
      pendingDeactivation = true
    }
  }

  override fun addNotify() {
    super.addNotify()
    val disposable = Disposer.newDisposable("FloatingToolbarContainer").also { this.disposable = it }
    if (hasCollapsibleToolbar || inactiveAlpha < 1.0) {
      setUpMouseListener(disposable)
    }
  }

  override fun removeNotify() {
    super.removeNotify()
    activationAnimator?.dispose()
    deactivationAnimator?.dispose()
    disposable?.let { Disposer.dispose(it) }
    disposable = null
    listeningToMouseEvents = false
  }

  private inner class ActivationAnimator(durationMillis: Int)
      : Animator("ActivationAnimator", numFrames(durationMillis), durationMillis, false) {

    private val initialActivationFactor = activationFactor

    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      activationFactor = (initialActivationFactor + frame / totalFrames.toDouble()).coerceIn(0.0, 1.0)
    }

    override fun paintCycleEnd() {
      activationFactor = 1.0
      dispose()
    }

    override fun dispose() {
      super.dispose()
      activationAnimator = null
      if (pendingDeactivation) {
        pendingDeactivation = false
        triggerDeactivation()
      }
    }
  }

  private inner class DeactivationAnimator(durationMillis: Int)
      : Animator("CollapseAnimator", numFrames(durationMillis + COLLAPSE_DELAY_MILLIS), durationMillis + COLLAPSE_DELAY_MILLIS, false) {

    private val initialActivationFactor = activationFactor
    private val delayFrames = numFrames(COLLAPSE_DELAY_MILLIS)

    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      if (frame <= delayFrames) {
        return
      }
      activationFactor = (initialActivationFactor - (frame - delayFrames) / (totalFrames - delayFrames).toDouble()).coerceIn(0.0, 1.0)
    }

    override fun paintCycleEnd() {
      activationFactor = 0.0
      dispose()
    }

    override fun dispose() {
      super.dispose()
      deactivationAnimator = null
    }
  }

  private inner class Layout : AbstractLayoutManager() {

    override fun preferredLayoutSize(container: Container): Dimension {
      require(container == this@FloatingToolbarContainer)
      return computeSize(collapsed = false)
    }

    override fun layoutContainer(container: Container) {
      require(container == this@FloatingToolbarContainer)
      val expandedSize = computeSize(collapsed = false)
      val collapsedSize = computeSize(collapsed = true)
      val insets = insets
      val rect = Rectangle(insets.left, insets.top, width - insets.left - insets.right, height - insets.top - insets.bottom)
      var expansion = (expandedSize[orientation] - collapsedSize[orientation]).scaled(expansionFactor)
      val offset = Dimension()
      offset[orientation] = rect.size[orientation] - collapsedSize[orientation] - expansion
      var pendingSpacer = false
      for (child in container.components) {
        if (pendingSpacer) {
          val spacerLength = min(expansion, SPACER_SIZE)
          expansion -= spacerLength
          offset.increment(orientation, spacerLength)
        }
        val childSize = child.preferredSize
        val minChildLength = child.preferredSize(collapsed = true)[orientation]
        val childExpansion = min(expansion, childSize[orientation] - minChildLength)
        childSize[orientation] = minChildLength + childExpansion
        child.setBounds(offset.width, offset.height, childSize.width, childSize.height)
        expansion -= childExpansion
        offset.increment(orientation, childSize[orientation])
        if (childSize[orientation] != 0) {
          pendingSpacer = true
        }
      }
    }

    private fun computeSize(collapsed: Boolean): Dimension {
      val size = Dimension()
      var pendingSpacer = false
      for (child in components) {
        val childSize = child.preferredSize(collapsed)
        if (childSize[orientation] != 0) {
          if (pendingSpacer) {
            size.increment(orientation, SPACER_SIZE)
          }
          size.combine(orientation, childSize)
          pendingSpacer = true
        }
      }
      return size
    }
  }
}

@Suppress("UnstableApiUsage")
private fun ActionToolbar.configureToolbar() {
  layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
  minimumButtonSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
  ((this as? ActionToolbarImpl)?.setActionButtonBorder(1, 1))
  component.apply {
    border = JBUI.Borders.empty(2)
    isOpaque = false
    putClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY, true)
  }
  makeNavigable()
}

private class ToolbarPanel(private val toolbar: ActionToolbar, val collapsible: Boolean) : BorderLayoutPanel() {

  private var bufferingPainter = VolatileImageBufferingPainter(Transparency.TRANSLUCENT)

  private val crossDimension
    get() = if (toolbar.orientation == HORIZONTAL) height else width
  private val cornerRadius
    get() = crossDimension / 2
  var alpha: Float by bufferingPainter::alpha

  init {
    isOpaque = false
    background = JBUI.CurrentTheme.Popup.toolbarPanelColor()
    layout = Layout()
    add(toolbar.component)
  }

  /** This property causes repaint of a child to trigger repaint of this panel. */
  override fun isPaintingOrigin(): Boolean = true

  override fun paintComponent(g: Graphics) {
    // Everything is painted by the paintChildren method.
  }

  override fun paintBorder(g: Graphics) {
    // Everything is painted by the paintChildren method.
  }

  override fun paintChildren(g: Graphics) {
    val outsideShape = createOutsideShape()
    bufferingPainter.paintBuffered(g, size) {
      paintWithTransparentCorners(it, outsideShape)
    }
  }

  private fun paintWithTransparentCorners(g2: Graphics2D, outsideShape: Shape) {
    setupAAPainting(g2)
    // Paint background.
    if (background != null) {
      g2.color = background
      g2.fillRect(0, 0, width, height)
    }
    // Paint children.
    super.paintChildren(g2)
    // Make corners transparent
    clearArea(g2, outsideShape)
    // Paint border.
    g2.color = SEPARATOR_COLOR
    g2.draw(createRoundRectangle(0, 0, width - 1, height - 1, cornerRadius))
  }

  private fun clearArea(g2: Graphics2D, area: Shape) {
    val config = disableAAPainting(g2) // Disable antialiasing for speed.
    val composite = g2.composite
    g2.composite = AlphaComposite.Clear
    g2.fill(area)
    g2.composite = composite
    config.restore()
  }

  private fun createOutsideShape(): Shape {
    return Path2D.Double(Path2D.WIND_EVEN_ODD).apply {
      append(createRoundRectangle(0, 0, width - 1, height - 1, cornerRadius), false)
      append(Rectangle(0, 0, width, height), false)
    }
  }

  override fun getMaximumSize(): Dimension =
      toolbar.component.getPreferredSize() + insets

  val collapsedSize: Dimension
    get() {
      val maxSize = getMaximumSize()
      if (collapsible) {
        val s = min(maxSize.width, maxSize.height)
        return Dimension(s, s)
      }
      return maxSize
    }

  private inner class Layout : AbstractLayoutManager() {

    private val orientation
      get() = toolbar.orientation

    override fun preferredLayoutSize(parent: Container): Dimension =
        toolbar.component.preferredSize + insets

    override fun layoutContainer(parent: Container) {
      val insets = insets
      val toolbarSize = toolbar.component.preferredSize
      val offset = calculateToolbarOffset(size - insets, toolbarSize)
      toolbar.component.setBounds(insets.left + offset.width, insets.top + offset.height, toolbarSize.width, toolbarSize.height)
    }

    private fun calculateToolbarOffset(availableSize: Dimension, preferredToolbarSize: Dimension): Dimension {
      val insets = toolbar.component.insets
      val available = (availableSize - insets)[orientation]
      val preferred = (preferredToolbarSize - insets)[orientation]
      if (preferred <= available) {
        return ZERO_DIMENSION
      }
      val anchorExtent = locateAnchorButton() ?: return ZERO_DIMENSION
      if (preferred <= anchorExtent.size) {
        return ZERO_DIMENSION
      }
      val d = anchorExtent.offset.scaled(preferred - available, preferred - anchorExtent.size)
      return if (orientation == HORIZONTAL) Dimension(-d, 0) else Dimension(0, -d)
    }

    private fun locateAnchorButton(): Extent? {
      var offset = 0
      var firstEnabled: Extent? = null
      var firstVisible: Extent? = null
      for (child in toolbar.component.components) {
        if (child is ActionButton && child.isVisible) {
          val childSize = child.preferredSize[orientation]
          if (child.isSelected) {
            return Extent(offset, childSize)
          }
          if (firstEnabled == null && child.isEnabled) {
            firstEnabled = Extent(offset, childSize)
          }
          if (firstVisible == null) {
            firstVisible = Extent(offset, childSize)
          }
          offset += childSize
        }
      }
      return firstEnabled ?: firstVisible
    }
  }

  class Extent(val offset: Int, val size: Int)
}

@MagicConstant(intValues = [HORIZONTAL.toLong(), VERTICAL.toLong()])
private annotation class Orientation

@Suppress("SameParameterValue")
private fun createRoundRectangle(x: Int, y: Int, w: Int, h: Int, cornerRadius: Int): RoundRectangle2D =
    RoundRectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble(), cornerRadius.toDouble(), cornerRadius.toDouble())

private fun Component.preferredSize(collapsed: Boolean): Dimension =
    if (collapsed) collapsedSize() else preferredSize

private fun Component.collapsedSize(): Dimension =
    if (this is ToolbarPanel && collapsible) collapsedSize else ZERO_DIMENSION

private fun Dimension.combine(@Orientation orientation: Int, other: Dimension) {
  if (orientation == HORIZONTAL) {
    width += other.width
    height = max(height, other.height)
  }
  else {
    width = max(width, other.width)
    height += other.height
  }
}

private fun Dimension.increment(@Orientation orientation: Int, value: Int) {
  if (orientation == HORIZONTAL) {
    width += value
  }
  else {
    height += value
  }
}

private operator fun Dimension.set(@Orientation orientation: Int, value: Int) {
  if (orientation == HORIZONTAL) {
    width = value
  }
  else {
    height = value
  }
}

private operator fun Dimension.get(@Orientation orientation: Int): Int =
    if (orientation == HORIZONTAL) width else height

private operator fun Dimension.minus(insets: Insets): Dimension =
    Dimension(width - insets.left - insets.right, height - insets.top - insets.bottom)

private operator fun Dimension.plus(insets: Insets): Dimension =
    Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom)

private operator fun Point.get(@Orientation orientation: Int): Int =
    if (orientation == HORIZONTAL) x else y

private operator fun Point.minus(point: Point): Point =
    Point(x - point.x, y - point.y)

private fun Int.scaled(numerator: Int, denominator: Int): Int =
    ((this.toLong() * numerator + denominator / 2) / denominator).toInt()

private fun numFrames(durationMillis: Int): Int = max(durationMillis.scaled(1000, ANIMATION_FRAMES_PER_SECOND), 1)

private const val ANIMATION_FRAMES_PER_SECOND = 60
private const val ACTIVATION_ANIMATION_DURATION_MILLIS = 200
private const val COLLAPSE_ANIMATION_DURATION_MILLIS = 400
private const val COLLAPSE_DELAY_MILLIS = 2000
private const val ACTIVE_ALPHA = 1.0

private val ZERO_DIMENSION = Dimension()

private val SPACER_SIZE = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width / 3
