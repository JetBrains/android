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
package com.android.tools.idea.streaming.emulator

import com.android.emulator.control.InputEvent as InputEventMessage
import com.android.emulator.control.Touch
import com.android.emulator.control.Touch.EventExpiration.NEVER_EXPIRE
import com.android.tools.adtui.util.scaled
import com.android.tools.idea.streaming.core.drawCircle
import com.android.tools.idea.streaming.core.fillCircle
import com.android.tools.idea.streaming.core.scaledUnbiased
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.InputEvent.BUTTON1_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import javax.swing.SwingConstants

/** Represents the touchpad of an AVD, e.g. AI glasses. */
internal class TouchpadPanel(
  private val emulator: EmulatorController,
  private val touchpadSize: Dimension,
) : BorderLayoutPanel() {

  private var multiTouchMode: Boolean = false
    set(value) {
      if (value != field) {
        terminateDragging()
        field = value
        repaint()
      }
    }

  /**
   * Last coordinates of the mouse pointer while the first button was pressed.
   * Set to null when the first mouse button is released.
   */
  private var lastMouseCoordinates: Point? = null
    set(value) {
      if (value != field) {
        field = value
        repaint()
      }
    }
  private var mouseButtonPressed = false
    set(value) {
      if (value != field) {
        field = value
        repaint()
      }
    }
  private val mouseListener = MyMouseListener()
  private val touches = Array(2) { id -> Touch.newBuilder().setIdentifier(id).setExpiration(NEVER_EXPIRE) }
  private val inputEvent = InputEventMessage.newBuilder()
  private val touchpadEvent = inputEvent.touchpadEventBuilder
  private var lastSentInputEvent: InputEventMessage? = null

  init {
    border = JBUI.Borders.customLine(JBColor.border())
    background = JBColor(0xF9FAFE, 0x393B40)
    val color = JBUI.CurrentTheme.Tooltip.grayedForeground()
    addToCenter(JBLabel("Hold shift for two-finger mode", SwingConstants.CENTER).apply { foreground = color })
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)
    addKeyListener(MyKeyListener())
  }

  override fun getPreferredSize(): Dimension {
    val insets = insets
    return Dimension(JBUIScale.scale(PREFERRED_HEIGHT.scaled(touchpadSize.width, touchpadSize.height)) + insets.width,
                     JBUIScale.scale(PREFERRED_HEIGHT) + insets.height)
  }

  override fun getMinimumSize(): Dimension = preferredSize

  override fun getMaximumSize(): Dimension = preferredSize

  override fun paintComponent(graphics: Graphics) {
    super.paintComponent(graphics)
    drawTouchFeedback(graphics)
  }

  private fun drawTouchFeedback(graphics: Graphics) {
    val point = lastMouseCoordinates ?: return
    val insets = insets
    val w = width - insets.width
    val h = height - insets.height
    val touchpadRectangle = Rectangle(insets.left, insets.top, w, h)
    if (!touchpadRectangle.contains(point)) {
      return
    }

    val g = graphics.create() as Graphics2D
    g.setRenderingHints(RenderingHints(mapOf(RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
                                             RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY)))
    g.clip = touchpadRectangle
    g.color = JBUI.CurrentTheme.Tooltip.grayedForeground()
    val scale = w.toDouble() / touchpadSize.width
    val radius = TOUCH_FEEDBACK_RADIUS.scaled(scale)
    if (multiTouchMode) {
      val fingerDistance = FINGER_HALF_DISTANCE.scaled(scale)
      point.x -= fingerDistance / 2
      drawTouchFeedback(g, point, radius)
      point.x += fingerDistance
      drawTouchFeedback(g, point, radius)
    }
    else {
      drawTouchFeedback(g, point, radius)
    }
  }

  private fun drawTouchFeedback(g: Graphics2D, point: Point, radius: Int) {
    if (mouseButtonPressed) {
      g.fillCircle(point, radius)
    }
    else {
      g.drawCircle(point, radius)
    }
  }

  private fun updateMouseState(event: MouseEvent) {
    val modifiers = event.modifiersEx
    lastMouseCoordinates = Point(event.x, event.y)
    multiTouchMode = (modifiers and SHIFT_DOWN_MASK) != 0
    mouseButtonPressed = (modifiers and BUTTON1_DOWN_MASK) != 0
  }

  // Terminates ongoing dragging if any.
  private fun terminateDragging() {
    touchpadEvent.clearTouches()
    for (touch in touches) {
      if (touch.pressure != 0) {
        touch.pressure = 0
        touchpadEvent.addTouches(touch)
      }
    }
    sendTouchpadEventIfNotEmpty()
  }

  private fun sendMouseEvent(x: Int, y: Int, withPressure: Boolean) {
    val w = width - insets.width
    val h = height - insets.height

    val pressure = if (withPressure) PRESSURE_RANGE_MAX else 0
    // Touchpad coordinates.
    val touchpadX = (x - insets.left).scaledUnbiased(w, touchpadSize.width)
    val touchpadY = (y - insets.top).scaledUnbiased(h, touchpadSize.height)
    touchpadEvent.clearTouches()
    if (multiTouchMode) {
      addTouchWithAdjustments(0, touchpadX - FINGER_HALF_DISTANCE, touchpadY, pressure)
      addTouchWithAdjustments(1, touchpadX + FINGER_HALF_DISTANCE, touchpadY, pressure)
    }
    else {
      addTouchWithAdjustments(0, touchpadX, touchpadY, pressure)
    }
    sendTouchpadEventIfNotEmpty()
  }

  private fun addTouchWithAdjustments(id: Int, x: Int, y: Int, pressure: Int) {
    if (isInsideTouchpad(x, y)) {
      addTouch(id, x, y, pressure)
    }
    else if (touches[id].pressure != 0) {
      // The pointer crosses the touchpad boundary while dragging.
      addTouch(id, x.coerceIn(0, touchpadSize.width - 1), y.coerceIn(0, touchpadSize.height - 1), 0)
    }
  }

  private fun addTouch(id: Int, x: Int, y: Int, pressure: Int) {
    val touch = touches[id]
    touch.x = x
    touch.y = y
    touch.pressure = pressure
    touchpadEvent.addTouches(touch)
  }

  private fun sendTouchpadEventIfNotEmpty() {
    if (touchpadEvent.touchesCount > 0) {
      val inputEvent = this@TouchpadPanel.inputEvent.build()
      if (inputEvent != lastSentInputEvent) {
        emulator.getOrCreateInputEventSender().onNext(inputEvent)
        lastSentInputEvent = inputEvent
      }
    }
  }

  private fun isInsideTouchpad(x: Int, y: Int): Boolean =
      0 <= x && x < touchpadSize.width && 0 <= y && y < touchpadSize.height

  private fun createTouch(identifier: Int): Touch.Builder =
      Touch.newBuilder().setIdentifier(identifier).setExpiration(NEVER_EXPIRE)

  private inner class MyMouseListener : MouseAdapter() {

    override fun mousePressed(event: MouseEvent) {
      requestFocusInWindow()
      updateMouseState(event)
      if (mouseButtonPressed) {
        sendMouseEvent(event.x, event.y, true)
      }
    }

    override fun mouseReleased(event: MouseEvent) {
      updateMouseState(event)
      if (event.button == BUTTON1) {
        sendMouseEvent(event.x, event.y, false)
      }
    }

    override fun mouseExited(event: MouseEvent) {
      if (mouseButtonPressed) {
        // Moving over the edge of the display view will terminate the ongoing dragging.
        sendMouseEvent(event.x, event.y, false)
      }
      lastMouseCoordinates = null
      mouseButtonPressed = false
    }

    override fun mouseDragged(event: MouseEvent) {
      updateMouseState(event)
      if (mouseButtonPressed) {
        sendMouseEvent(event.x, event.y, true)
      }
    }

    override fun mouseMoved(event: MouseEvent) {
      updateMouseState(event)
    }
  }

  private inner class MyKeyListener : KeyAdapter() {

    override fun keyPressed(event: KeyEvent) {
      updateMultiTouchMode(event)
    }

    override fun keyReleased(event: KeyEvent) {
      updateMultiTouchMode(event)
    }

    private fun updateMultiTouchMode(event: KeyEvent) {
      multiTouchMode = (event.modifiersEx and SHIFT_DOWN_MASK) != 0
    }
  }
}

private const val PREFERRED_HEIGHT = 40

private const val TOUCH_FEEDBACK_RADIUS: Int = 100
private const val FINGER_HALF_DISTANCE: Int = 260