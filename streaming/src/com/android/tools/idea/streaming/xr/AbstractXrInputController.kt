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
package com.android.tools.idea.streaming.xr

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.EmulatorSettings
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_KP_DOWN
import java.awt.event.KeyEvent.VK_KP_LEFT
import java.awt.event.KeyEvent.VK_KP_RIGHT
import java.awt.event.KeyEvent.VK_KP_UP
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseWheelEvent
import kotlin.math.PI

/** Distance of translational movement in meters in response to a discrete user action, e.g. pressing Ctrl+Plus. */
internal const val TRANSLATION_STEP_SIZE: Float = 0.5F

/**
 * Orchestrates mouse and keyboard input for XR devices. Keeps track of XR environment and passthrough.
 * Thread safe.
 */
internal abstract class AbstractXrInputController : Disposable {

  @Volatile var environment: XrEnvironment? = null
    set(value) {
      requireNotNull(value)
      if (field != value) {
        field = value
        ActivityTracker.getInstance().inc()
      }
    }
  @Volatile var passthroughCoefficient: Float = UNKNOWN_PASSTHROUGH_COEFFICIENT
    set(value) {
      require(value >= 0)
      if (field != value) {
        field = value
        ActivityTracker.getInstance().inc()
      }
    }

  @Volatile var inputMode: XrInputMode =
      if (StudioFlags.EMBEDDED_EMULATOR_XR_HAND_TRACKING.get()) XrInputMode.HAND else XrInputMode.HARDWARE
    @UiThread set(value) {
      if (field != value) {
        if (!areNavigationKeysEnabled(value)) {
          pressedKeysMask = 0 // Reset keyboard navigation state.
          mouseDragReferencePoint = null
        }
        field = value
      }
    }

  private var pressedKeysMask = 0
    set(value) {
      if (field != value) {
        field = value
        navigationMask = pressedKeysMaskToNavigationMask(value)
      }
    }

  private var navigationMask = 0
    set(value) {
      if (field != value) {
        val oldValue = field
        field = value
        sendVelocityUpdate(value, oldValue) // Send update to the emulator.
      }
    }

  protected var mouseDragReferencePoint: Point? = null
  private val emulatorSettings = EmulatorSettings.getInstance()
  private val controlKeys
    get() = emulatorSettings.cameraVelocityControls.keys

  /** Controls passthrough mode on the device. */
  abstract suspend fun setPassthrough(passthroughCoefficient: Float)

  /** Sends a command to move in the virtual space. The distances are in meters. */
  abstract fun sendTranslation(x: Float, y: Float, z: Float)

  /**
   * Notifies the controller that a key was pressed.
   * Returns true if the input event has been consumed.
   */
  @UiThread
  fun keyPressed(event: KeyEvent): Boolean {
    if (!areNavigationKeysEnabled(inputMode)) {
      return false
    }
    if (event.modifiersEx != 0) {
      pressedKeysMask = 0
      return false
    }
    val mask = keyToMask(event.keyCode)
    if (mask == 0) {
      return false
    }
    pressedKeysMask = pressedKeysMask or mask
    event.consume()
    return true
  }

  /**
   * Notifies the controller that a key was released.
   * Returns true if the input event has been consumed.
   */
  @UiThread
  fun keyReleased(event: KeyEvent): Boolean {
    if (!areNavigationKeysEnabled(inputMode)) {
      return false
    }
    if (event.modifiersEx != 0) {
      pressedKeysMask = 0
      return false
    }
    val mask = keyToMask(event.keyCode)
    if (mask == 0) {
      return false
    }
    pressedKeysMask = pressedKeysMask and mask.inv()
    event.consume()
    return true
  }

  /**
   * Notifies the controller that a mouse button was pressed.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mousePressed(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation()) {
      return false
    }
    if (event.button == BUTTON1) {
      mouseDragReferencePoint = event.point
    }
    event.consume()
    return true
  }

  /**
   * Notifies the controller that a mouse button was released.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mouseReleased(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation()) {
      return false
    }
    if (event.button == BUTTON1) {
      mouseDragReferencePoint = null
    }
    event.consume()
    return true
  }

  /**
   * Notifies the controller that the mouse entered the panel sending events to this controller.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mouseEntered(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation()) {
      return false
    }
    if (event.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK != 0) {
      mouseDragReferencePoint = event.point
    }
    event.consume()
    return true
  }

  /**
   * Notifies the controller that the mouse exited the panel sending events to this controller.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mouseExited(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    return false
  }

  /**
   * Notifies the controller that the mouse button was dragged.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  abstract fun mouseDragged(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean

  /**
   * Notifies the controller that the mouse was moved.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mouseMoved(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation()) {
      return false
    }
    event.consume()
    return true
  }

  /**
   * Notifies the controller that the mouse wheel was moved.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  abstract fun mouseWheelMoved(event: MouseWheelEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean

  private fun areNavigationKeysEnabled(inputMode: XrInputMode): Boolean {
    return when (inputMode) {
      XrInputMode.VIEW_DIRECTION, XrInputMode.LOCATION_IN_SPACE_XY, XrInputMode.LOCATION_IN_SPACE_Z -> true
      else -> false
    }
  }

  private fun keyToMask(keyCode: Int): Int {
    val index = controlKeys.indexOf(keyCode.toChar())
    if (index >= 0) {
      return 1 shl index
    }
    return when (keyCode) {
      VK_RIGHT, VK_KP_RIGHT -> NavigationKey.ROTATE_RIGHT.mask
      VK_LEFT, VK_KP_LEFT -> NavigationKey.ROTATE_LEFT.mask
      VK_UP, VK_KP_UP -> NavigationKey.ROTATE_UP.mask
      VK_DOWN, VK_KP_DOWN -> NavigationKey.ROTATE_DOWN.mask
      VK_PAGE_UP -> NavigationKey.ROTATE_RIGHT_UP.mask
      VK_PAGE_DOWN -> NavigationKey.ROTATE_RIGHT_DOWN.mask
      VK_HOME -> NavigationKey.ROTATE_LEFT_UP.mask
      VK_END -> NavigationKey.ROTATE_LEFT_DOWN.mask
      else -> 0
    }
  }

  /**
   * Replaces mask bits corresponding to the diagonal rotation numpad keys by combinations of bits
   * corresponding to horizontal and vertical rotation keys. Also cancels out keys that act in
   * opposite directions, e.g. [NavigationKey.ROTATE_RIGHT] and [NavigationKey.ROTATE_LEFT].
   */
  private fun pressedKeysMaskToNavigationMask(pressedKeysMask: Int): Int {
    var mask = pressedKeysMask and (NavigationKey.ROTATE_RIGHT_UP.mask - 1)
    if (pressedKeysMask and NavigationKey.ROTATE_RIGHT_UP.mask != 0) {
      mask = mask or NavigationKey.ROTATE_RIGHT.mask or NavigationKey.ROTATE_UP.mask
    }
    if (pressedKeysMask and NavigationKey.ROTATE_RIGHT_DOWN.mask != 0) {
      mask = mask or NavigationKey.ROTATE_RIGHT.mask or NavigationKey.ROTATE_DOWN.mask
    }
    if (pressedKeysMask and NavigationKey.ROTATE_LEFT_UP.mask != 0) {
      mask = mask or NavigationKey.ROTATE_LEFT.mask or NavigationKey.ROTATE_UP.mask
    }
    if (pressedKeysMask and NavigationKey.ROTATE_LEFT_DOWN.mask != 0) {
      mask = mask or NavigationKey.ROTATE_LEFT.mask or NavigationKey.ROTATE_DOWN.mask
    }
    // Cancel out keys acting in opposite directions.
    val opposites = intArrayOf(
        NavigationKey.MOVE_RIGHT.mask or NavigationKey.MOVE_LEFT.mask,
        NavigationKey.MOVE_UP.mask or NavigationKey.MOVE_DOWN.mask,
        NavigationKey.MOVE_BACKWARD.mask or NavigationKey.MOVE_FORWARD.mask,
        NavigationKey.ROTATE_RIGHT.mask or NavigationKey.ROTATE_LEFT.mask,
        NavigationKey.ROTATE_UP.mask or NavigationKey.ROTATE_DOWN.mask)
    for (m in opposites) {
      if ((mask and m) == m) {
        mask = mask and m.inv()
      }
    }
    return mask and (NavigationKey.TRANSLATION_MASK or NavigationKey.ROTATION_MASK)
  }

  protected abstract fun sendVelocityUpdate(newMask: Int, oldMask: Int)

  protected fun isMouseUsedForNavigation(): Boolean {
    return when (inputMode) {
      XrInputMode.VIEW_DIRECTION, XrInputMode.LOCATION_IN_SPACE_XY, XrInputMode.LOCATION_IN_SPACE_Z -> true
      else -> false
    }
  }

  companion object {
    internal const val UNKNOWN_PASSTHROUGH_COEFFICIENT = -1f

    const val MOUSE_WHEEL_NAVIGATION_FACTOR = 0.25F

    /** Distance of translational movement in meters when moving mouse across the device display. */
    const val TRANSLATION_SCALE = 4f
    /** Angle of rotation in radians when moving mouse across the device display. */
    const val ROTATION_SCALE = PI.toFloat()
    /** Translational velocity in meters per second. */
    const val VELOCITY = 1f
    /** Angular velocity in radians per second. */
    const val ANGULAR_VELOCITY = (PI / 6).toFloat()
  }

  protected enum class NavigationKey {
    // Translation keys.
    MOVE_FORWARD,      // W
    MOVE_LEFT,         // A
    MOVE_BACKWARD,     // S
    MOVE_RIGHT,        // D
    MOVE_DOWN,         // Q
    MOVE_UP,           // E
    // Rotation keys.
    ROTATE_RIGHT,      // Right arrow
    ROTATE_LEFT,       // Left arrow
    ROTATE_UP,         // Up arrow
    ROTATE_DOWN,       // Down arrow
    // Combination rotation keys.
    ROTATE_RIGHT_UP,   // Page Up
    ROTATE_RIGHT_DOWN, // Page Down
    ROTATE_LEFT_UP,    // Home
    ROTATE_LEFT_DOWN;  // End

    val mask: Int = 1 shl ordinal
    val cumulativeMask: Int
      get() = mask or (mask - 1)

    companion object {
      val TRANSLATION_MASK: Int = MOVE_UP.cumulativeMask
      val ROTATION_MASK: Int = ROTATE_DOWN.cumulativeMask and TRANSLATION_MASK.inv()
    }
  }
}

internal enum class XrInputMode {
  /** Mouse is used to interact with running apps simulating hand tracking. */
  HAND,
  /** Mouse is used to interact with running apps simulating eye tracking. */
  EYE,
  /** Mouse and keyboard events are transparently forwarded to the device. */
  HARDWARE,
  /** Relative mouse coordinates control view direction. */
  VIEW_DIRECTION,
  /** Relative mouse coordinates control location in x-y plane. Mouse wheel controls moving forward and back. */
  LOCATION_IN_SPACE_XY,
  /** Relative mouse y coordinate controls moving forward and back. */
  LOCATION_IN_SPACE_Z,
}
