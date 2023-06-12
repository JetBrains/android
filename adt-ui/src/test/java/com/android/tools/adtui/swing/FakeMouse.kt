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
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.concurrent.TimeUnit

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

  private fun press(x: Int, y: Int, button: Button, clickCount: Int): Cursor {
    check(cursor == null) { "Mouse already pressed. Call release before pressing again." }
    dispatchMouseEvent(MouseEvent.MOUSE_PRESSED, x, y, button, clickCount, button == Button.RIGHT)
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
    val target = point?.component
    val focus = this.focus
    if (target !== focus) {
      if (focus != null) {
        val relative = fakeUi.toRelative(focus, x, y)
        val relativePoint = RelativePoint(focus, relative.x, relative.y)
        dispatchMouseEvent(relativePoint, MouseEvent.MOUSE_EXITED, cursor.button.mask, 0, 1, false)
      }
      if (target != null) {
        dispatchMouseEvent(point, MouseEvent.MOUSE_ENTERED, cursor.button.mask, 0, 1, false)
      }
    }
    if (target != null) {
      dispatchMouseEvent(MouseEvent.MOUSE_DRAGGED, x, y, cursor.button, 1, false)
      this.cursor = Cursor(cursor, x, y)
    }
    this.focus = target
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
    val point = fakeUi.targetMouseEvent(x, y)
    val target = point?.component
    val focus = this.focus
    if (target !== focus) {
      if (focus != null) {
        val converted = fakeUi.toRelative(focus, x, y)
        dispatchMouseEvent(RelativePoint(focus, converted.x, converted.y), MouseEvent.MOUSE_EXITED, 0, 0, 1, false)
      }
      if (target != null) {
        dispatchMouseEvent(point, MouseEvent.MOUSE_ENTERED, 0, 0, 1, false)
      }
    }
    if (target != null) {
      dispatchMouseEvent(point, MouseEvent.MOUSE_MOVED, 0, 0, 1, false)
    }
    this.focus = target
  }

  fun release() {
    val cursor = this.cursor ?: throw IllegalStateException("Mouse not pressed. Call press before releasing.")
    dispatchMouseEvent(MouseEvent.MOUSE_RELEASED, cursor.x, cursor.y, cursor.button, 1, false)
    this.cursor = null
  }

  /**
   * Convenience method which calls [press] and [release] in turn and ensures that a clicked event is fired.
   *
   * For the key event to be handled by its target component, it is sometimes necessary to call
   * [com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue].
   */
  @JvmOverloads
  fun click(x: Int, y: Int, button: Button = Button.LEFT) {
    click(x, y, button, 1)
  }

  private fun click(x: Int, y: Int, button: Button, clickCount: Int) {
    check(cursor == null) { "Mouse already pressed. Call release before clicking." }
    moveTo(x, y)
    val cursor = press(x, y, button, clickCount)
    release()
    // PRESSED + RELEASED should additionally fire a CLICKED event
    dispatchMouseEvent(MouseEvent.MOUSE_CLICKED, cursor.x, cursor.y, cursor.button, clickCount, false)
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
    click(x, y, button, 1)
    click(x, y, button, 2)
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

  fun press(point: Point) {
    press(point.x, point.y, Button.LEFT)
  }

  fun rightClick(x: Int, y: Int) {
    click(x, y, Button.RIGHT, 1)
  }

  /**
   * Scrolls the mouse unit [rotation] clicks. Negative values mean scroll up / away,
   * positive values mean scroll down / towards.
   */
  fun wheel(x: Int, y: Int, rotation: Int) {
    dispatchMouseWheelEvent(x, y, rotation)
  }

  private fun dispatchMouseEvent(eventType: Int, x: Int, y: Int, button: Button, clickCount: Int, popupTrigger: Boolean) {
    val point = fakeUi.targetMouseEvent(x, y)
    if (point != null) {
      // Rare, but can happen if, say, a release mouse event closes a component, and then we try to
      // fire a followup clicked event on it.
      dispatchMouseEvent(point, eventType, button.mask, button.code, clickCount, popupTrigger)
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
   */
  private fun dispatchMouseEvent(
      point: RelativePoint, eventType: Int, modifiers: Int, button: Int, clickCount: Int, popupTrigger: Boolean) {
    val event = MouseEvent(
        point.component, eventType, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), keyboard.toModifiersCode() or modifiers,
        point.x, point.y, clickCount, popupTrigger, button)
    point.component.dispatchEvent(event)
  }

  private fun dispatchMouseWheelEvent(x: Int, y: Int, rotation: Int) {
    val point = fakeUi.targetMouseEvent(x, y) ?: return
    val event = MouseWheelEvent(
        point.component, MouseEvent.MOUSE_WHEEL, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), keyboard.toModifiersCode(),
        point.x, point.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation)
    point.component.dispatchEvent(event)
  }

  enum class Button(val code: Int, val mask: Int) {
    LEFT(MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK),
    CTRL_LEFT(MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK or InputEvent.CTRL_DOWN_MASK),
    RIGHT(MouseEvent.BUTTON3, InputEvent.BUTTON3_DOWN_MASK),
    MIDDLE(MouseEvent.BUTTON2, InputEvent.BUTTON2_DOWN_MASK)
  }

  private class Cursor(val button: Button, val x: Int, val y: Int) {
    constructor(previous: Cursor, x: Int, y: Int) : this(previous.button, x, y)
  }
}