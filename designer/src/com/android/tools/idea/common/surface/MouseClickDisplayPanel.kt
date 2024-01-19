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

import com.android.tools.adtui.stdui.setColorAndAlpha
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

private const val DEFAULT_DURATION_MS = 100L
private val DEFAULT_DECORATION_COLOR = Color(0x10, 0xA0, 0x90, 0x50)
private const val DEFAULT_START_RADIUS = 10
private const val DEFAULT_END_RADIUS = DEFAULT_START_RADIUS + 10

/**
 * A panel that displays the mouse clicks when contained in the coordinates of this panel. After
 * creation, [enableMouseClickDisplay] needs to be called to enable the bubble display. By default,
 * the component is disabled. Enable it by calling [setEnabled].
 *
 * @param parentDisposable Required [Disposable] to attach this [MouseClickDisplayPanel]. When the
 *   parent is disposed, the listeners used by this panel will be removed.
 * @param durationMs Duration of the ripple animation in seconds.
 * @param decorationColor Color of the ripple.
 * @param startRadius Start radius for the ripple.
 * @param endRadius End radius for the ripple.
 */
class MouseClickDisplayPanel
@JvmOverloads
constructor(
  parentDisposable: Disposable,
  private val durationMs: Long = DEFAULT_DURATION_MS,
  private val decorationColor: Color = DEFAULT_DECORATION_COLOR,
  private val startRadius: Int = DEFAULT_START_RADIUS,
  private val endRadius: Int = DEFAULT_END_RADIUS,
) : JComponent(), Disposable {
  /** True if the bubble drawing is enabled. */
  private var isMouseTrackingEnabled: Boolean = false

  /** End timestamp of the bubble animation. */
  private var endTimeMillis: Long = Long.MAX_VALUE

  /** After a click, this contains the position within the component where the click happened. */
  private var clickPosition: Point? = null

  /** The click listener to handle the bubble triggering. */
  private val clickListener = AWTEventListener {
    if (it !is MouseEvent) return@AWTEventListener

    if (it.getID() == MouseEvent.MOUSE_CLICKED && it.clickCount == 1 && isEnabled && isVisible) {
      // Convert the coordinates from the source component coordinates space to the
      // MouseCLickDisplayPanel space.
      val clickPoint = SwingUtilities.convertPoint(it.component, it.point, this)
      if (contains(clickPoint)) {
        // The click is within our bounds (0, 0, width, height)
        clickPosition = clickPoint
        endTimeMillis = System.currentTimeMillis() + durationMs
        repaint()
      }
    }
  }

  init {
    Disposer.register(parentDisposable, this)

    isOpaque = false
    isFocusable = false
    isEnabled = false
  }

  /**
   * Starts the mouse click display. If there is a click within the bounds, the click will be drawn.
   */
  private fun enableMouseClickDisplay() {
    if (!isMouseTrackingEnabled) {
      // We do not use addMouseListener to avoid the component intercepting clicks that are meant
      // for other components.
      Toolkit.getDefaultToolkit().addAWTEventListener(clickListener, AWTEvent.MOUSE_EVENT_MASK)
      isMouseTrackingEnabled = true
    }
  }

  /** Stops the mouse click display. */
  private fun disableMouseClickDisplay() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(clickListener)
    isMouseTrackingEnabled = false
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)

    if (enabled) enableMouseClickDisplay() else disableMouseClickDisplay()
  }

  override fun paintComponent(g: Graphics) {
    clickPosition?.let {
      val animationTime = endTimeMillis - System.currentTimeMillis()
      if (animationTime < 0) {
        clickPosition = null
        // Animations is over
        return
      }

      val animationPosition = (durationMs - animationTime) / durationMs.toFloat()

      val g2d = g.create() as Graphics2D
      try {
        g2d.setColorAndAlpha(decorationColor)
        val radius = startRadius + (endRadius * animationPosition).toInt()
        g2d.fillOval(it.x - radius, it.y - radius, radius * 2, radius * 2)
      } finally {
        g2d.dispose()
      }
      repaint()
    }
  }

  override fun dispose() {
    disableMouseClickDisplay()
  }
}
