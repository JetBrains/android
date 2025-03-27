/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.swing

import com.android.tools.adtui.swing.FakeUi.RelativePoint
import java.awt.Component
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.InputEvent.BUTTON1_DOWN_MASK
import java.awt.event.InputEvent.BUTTON2_DOWN_MASK
import java.awt.event.InputEvent.BUTTON3_DOWN_MASK
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseEvent.BUTTON2
import java.awt.event.MouseEvent.BUTTON3
import java.awt.event.MouseEvent.MOUSE_CLICKED
import java.awt.event.MouseEvent.MOUSE_DRAGGED
import java.awt.event.MouseEvent.MOUSE_ENTERED
import java.awt.event.MouseEvent.MOUSE_EXITED
import java.awt.event.MouseEvent.MOUSE_MOVED
import java.awt.event.MouseEvent.MOUSE_PRESSED
import java.awt.event.MouseEvent.MOUSE_RELEASED
import java.awt.event.MouseEvent.MOUSE_WHEEL
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL

/**
 * A fake mouse device that can be used for clicking on / scrolling programmatically in tests.
 *
 * Do not instantiate directly - use [FakeUi.mouse] instead.
 */
class FakeMouse internal constructor(private val fakeUi: FakeUi, private val keyboard: FakeKeyboard) {
  private var cursor: Cursor? = null

  /**
   * Returns the component underneath the mouse cursor, the one which will receive any dispatched
   * mouse events.
   *
   * Note: This value is potentially updated after every call to [moveTo].
   */
  var focus: Component? = null

  /**
   * Begins holding down a mouse button. Can be dragged with [dragTo] and eventually should
   * be released by [release].
   */
  @JvmOverloads
  fun press(x: Int, y: Int, button: Button = Button.LEFT) {
    press(x, y, button, 1)
  }

  @JvmOverloads
  fun press(point: Point, button: Button = Button.LEFT) {
    press(point.x, point.y, button, 1)
  }

  private fun press(x: Int, y: Int, button: Button, clickCount: Int, timestamp: Long = System.currentTimeMillis()): Cursor {
    check(cursor == null) { "Mouse already pressed. Call release before pressing again." }
    dispatchMouseEvent(MOUSE_PRESSED, x, y, button, clickCount, button == Button.RIGHT, timestamp)
    return Cursor(button, x, y).also { cursor = it }
  }

  fun dragTo(point: Point) {
    dragTo(point.x, point.y)
  }

  /**
   * Simulates dragging mouse pointer from the current position (determined by [cursor])
   * to the given point. In addition to a MOUSE_DRAGGED event, may generate MOUSE_ENTERED
   * and/or MOUSE_EXITED events, if the mouse pointer crosses component boundaries.
   */
  fun dragTo(x: Int, y: Int) {
    val cursor = this.cursor ?: throw IllegalStateException("Mouse not pressed. Call press before dragging.")
    val point = fakeUi.targetMouseEvent(x, y)
    val timestamp = System.currentTimeMillis()
    val target = point?.component
    val focus = this.focus
    if (target !== focus) {
      if (focus != null) {
        val relative = fakeUi.toRelative(focus, x, y)
        val relativePoint = RelativePoint(focus, relative.x, relative.y)
        dispatchMouseEvent(relativePoint, MOUSE_EXITED, cursor.button.mask, 0, 1, false, timestamp)
      }
      if (target != null) {
        dispatchMouseEvent(point, MOUSE_ENTERED, cursor.button.mask, 0, 1, false, timestamp)
      }
    }
    if (target != null) {
      dispatchMouseEvent(MOUSE_DRAGGED, x, y, cursor.button, 1, false, timestamp)
      this.cursor = Cursor(cursor, x, y)
    }
  }

  /** Like [dragTo] but with relative values. */
  fun dragDelta(xDelta: Int, yDelta: Int) {
    val cursor = this.cursor ?: throw IllegalStateException("Mouse not pressed. Call press before dragging.")
    dragTo(cursor.x + xDelta, cursor.y + yDelta)
  }

  fun moveTo(point: Point) {
    moveTo(point.x, point.y)
  }

  /**
   * Simulates moving mouse pointer from the current position (determined by [cursor])
   * to the given point. In addition to a MOUSE_DRAGGED event, may generate MOUSE_ENTERED
   * and/or MOUSE_EXITED events, if the mouse pointer crosses component boundaries.
   */
  fun moveTo(x: Int, y: Int) {
    moveTo(x, y, System.currentTimeMillis())
  }

  private fun moveTo(x: Int, y: Int, timestamp: Long) {
    val point = fakeUi.targetMouseEvent(x, y)
    preprocessMouseEvent(point ?: RelativePoint(fakeUi.root, x, y), MOUSE_MOVED, 0, 0, 0, false, timestamp)
    val target = point?.component
    val focus = this.focus
    if (target !== focus) {
      if (focus != null) {
        val converted = fakeUi.toRelative(focus, x, y)
        dispatchMouseEvent(RelativePoint(focus, converted.x, converted.y), MOUSE_EXITED, 0, 0, 0, false, timestamp)
      }
      if (target != null) {
        dispatchMouseEvent(point, MOUSE_ENTERED, 0, 0, 0, false, timestamp)
      }
    }
    if (target != null) {
      dispatchMouseEvent(point, eventType = MOUSE_MOVED, 0, 0, 0, false, timestamp)
    }
  }

  fun preprocessMouseEvent(
      point: RelativePoint, eventType: Int, modifiers: Int, button: Int, clickCount: Int, popupTrigger: Boolean, timestamp: Long) {
    val glassPane = fakeUi.glassPane ?: return
    val event = MouseEvent(point.component, eventType, timestamp, keyboard.toModifiersCode() or modifiers, point.x, point.y, clickCount,
                           popupTrigger, button)
    glassPane.dispatch(event)
  }

  fun release() {
    release(System.currentTimeMillis())
  }

  private fun release(timestamp: Long) {
    val cursor = this.cursor ?: throw IllegalStateException("Mouse not pressed. Call press before releasing.")
    this.cursor = null
    val x = cursor.x
    val y = cursor.y
    val button = cursor.button
    val point = fakeUi.targetMouseEvent(x, y)
    // The DOWN_MASK bit for released button should be 0 for MOUSE_RELEASED events.
    val modifiers = button.mask and (BUTTON1_DOWN_MASK or BUTTON2_DOWN_MASK or BUTTON3_DOWN_MASK).inv()
    preprocessMouseEvent(point ?: RelativePoint(fakeUi.root, x, y), MOUSE_RELEASED, modifiers, button.code, 0, false, timestamp)
    if (point != null) {
      dispatchMouseEvent(point, MOUSE_RELEASED, modifiers, button.code, 0, false, timestamp)
    }
  }

  /**
   * Convenience method which calls [press] and [release] in turn and ensures that a clicked event is fired.
   *
   * For the key event to be handled by its target component, it is sometimes necessary to call
   * [com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue].
   */
  @JvmOverloads
  fun click(x: Int, y: Int, button: Button = Button.LEFT) {
    click(x, y, button, 1, System.currentTimeMillis())
  }

  private fun click(x: Int, y: Int, button: Button, clickCount: Int, timestamp: Long) {
    check(cursor == null) { "Mouse already pressed. Call release before clicking." }
    moveTo(x, y, timestamp)
    val cursor = press(x, y, button, clickCount, timestamp)
    release(timestamp)
    // PRESSED + RELEASED should additionally fire a CLICKED event
    dispatchMouseEvent(MOUSE_CLICKED, cursor.x, cursor.y, cursor.button, clickCount, false, timestamp)
  }

  /**
   * Convenience method which calls [click] twice in quick succession.
   *
   * For the key event to be handled by its target component, it is sometimes necessary to call
   * [com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue].
   */
  @JvmOverloads
  fun doubleClick(x: Int, y: Int, button: Button = Button.LEFT) {
    check(cursor == null) { "Mouse already pressed. Call release before double-clicking." }
    val timestamp = System.currentTimeMillis()
    click(x, y, button, 1, timestamp)
    click(x, y, button, 2, timestamp)
  }

  /**
   * Convenience method which calls [press], [dragTo], and [release] in turn.
   */
  @JvmOverloads
  fun drag(xStart: Int, yStart: Int, xDelta: Int, yDelta: Int, button: Button = Button.LEFT) {
    check(cursor == null) { "Mouse already pressed. Call release before dragging." }
    val xTo = xStart + xDelta
    val yTo = yStart + yDelta
    press(xStart, yStart, button)
    dragTo(xTo, yTo)
    release()
  }

  fun rightClick(x: Int, y: Int) {
    click(x, y, Button.RIGHT, 1, System.currentTimeMillis())
  }

  /**
   * Scrolls the mouse unit [rotation] clicks. Negative values mean scroll up / away,
   * positive values mean scroll down / towards.
   */
  fun wheel(x: Int, y: Int, rotation: Int) {
    dispatchMouseWheelEvent(x, y, rotation)
  }

  private fun dispatchMouseEvent(eventType: Int, x: Int, y: Int, button: Button, clickCount: Int, popupTrigger: Boolean, timestamp: Long) {
    val point = fakeUi.targetMouseEvent(x, y)
    preprocessMouseEvent(
        point ?: RelativePoint(fakeUi.root, x, y), eventType, button.mask, button.code, clickCount, popupTrigger, timestamp)
    if (point != null) {
      // Rare, but can happen if, say, a release mouse event closes a component, and then we try to
      // fire a followup clicked event on it.
      dispatchMouseEvent(point, eventType, button.mask, button.code, clickCount, popupTrigger, timestamp)
    }
  }

  /**
   * Dispatches a mouse event.
   *
   * @param point Point that supplies the component and coordinates to trigger mouse event.
   * @param eventType Type of mouse event to trigger.
   * @param modifiers Mouse modifier codes.
   * @param button Which mouse button to pass to the event.
   * @param clickCount The number of consecutive clicks passed in the event. This is not how many
   *     times to issue the event but how many times a click has occurred.
   * @param popupTrigger should this event trigger a popup menu.
   * @param timestamp the timestamp of the event is milliseconds since epoch
   */
  private fun dispatchMouseEvent(
      point: RelativePoint, eventType: Int, modifiers: Int, button: Int, clickCount: Int, popupTrigger: Boolean, timestamp: Long) {
    val event = MouseEvent(
        point.component, eventType, timestamp, keyboard.toModifiersCode() or modifiers, point.x, point.y, clickCount, popupTrigger, button)
    point.component.dispatchEvent(event)
    focus = if (eventType == MOUSE_EXITED) null else point.component
  }

  private fun dispatchMouseWheelEvent(x: Int, y: Int, rotation: Int) {
    val point = fakeUi.targetMouseEvent(x, y) ?: return
    val event = MouseWheelEvent(
      point.component, MOUSE_WHEEL, System.currentTimeMillis(), keyboard.toModifiersCode(),
      point.x, point.y, 0, false, WHEEL_UNIT_SCROLL, 1, rotation)
    point.component.dispatchEvent(event)
    focus = point.component
  }

  enum class Button(val code: Int, val mask: Int) {
    LEFT(BUTTON1, BUTTON1_DOWN_MASK),
    CTRL_LEFT(BUTTON1, BUTTON1_DOWN_MASK or InputEvent.CTRL_DOWN_MASK),
    RIGHT(BUTTON3, BUTTON3_DOWN_MASK),
    MIDDLE(BUTTON2, BUTTON2_DOWN_MASK)
  }

  private class Cursor(val button: Button, val x: Int, val y: Int) {
    constructor(previous: Cursor, x: Int, y: Int) : this(previous.button, x, y)
  }
}