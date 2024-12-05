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
package com.android.tools.idea.streaming.emulator.xr

import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.InputEvent
import com.android.emulator.control.XrInputEvent
import com.android.emulator.control.XrInputEvent.NavButtonPressEvent
import com.android.emulator.control.XrInputEvent.RelativeMoveEvent
import com.android.tools.adtui.util.scaled
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.actions.HardwareInputStateStorage
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.ktor.util.collections.ConcurrentMap
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

// TODO: Adjust this coefficient when XR gRPC API is implemented.
private const val MOUSE_WHEEL_NAVIGATION_FACTOR = 120.0

/**
 * Orchestrates mouse and keyboard input for XR devices. Thread safe.
 */
internal class EmulatorXrInputController(private val emulator: EmulatorController): Disposable {

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
        navigationMask = computeNavigationMask(value)
      }
    }

  private var navigationMask = 0
    set(value) {
      if (field != value) {
        field = value
        sendKeyUpdate(value) // Send update to the emulator.
      }
    }

  private val emulatorSettings = EmulatorSettings.getInstance()
  private val controlKeys
    get() = emulatorSettings.cameraVelocityControls.keys
  private var mouseDragReferencePoint: Point? = null
  var mouseScaleFactor: Double = 1.0
  private val inputEvent = InputEvent.newBuilder()
  private val xrInputEvent = XrInputEvent.newBuilder()
  private val navigationEvent = NavButtonPressEvent.newBuilder()
  private val relativeMoveEvent = RelativeMoveEvent.newBuilder()

  init {
    Disposer.register(emulator, this)
  }

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
   */
  @UiThread
  fun mousePressed(event: MouseEvent): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
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
   */
  @UiThread
  fun mouseReleased(event: MouseEvent): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
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
   */
  @UiThread
  fun mouseEntered(event: MouseEvent): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
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
   */
  @UiThread
  fun mouseExited(event: MouseEvent): Boolean {
    return false
  }

  /**
   * Notifies the controller that the mouse button was dragged.
   * Returns true if the input event has been consumed.
   */
  @UiThread
  fun mouseDragged(event: MouseEvent): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
      return false
    }
    val referencePoint = mouseDragReferencePoint
    if (referencePoint != null) {
      val deltaX = if (inputMode == XrInputMode.LOCATION_IN_SPACE_Z) 0 else (event.x - referencePoint.x).scaled(mouseScaleFactor)
      val deltaY = (event.y - referencePoint.y).scaled(mouseScaleFactor)
      mouseDragReferencePoint = event.point
      if (deltaX != 0 || deltaY != 0) {
        relativeMoveEvent.setRelX(deltaX)
        relativeMoveEvent.setRelY(deltaY)
        when (inputMode) {
          XrInputMode.VIEW_DIRECTION -> relativeMoveEvent.intent = RelativeMoveEvent.Intent.XR_MOVE_EVENT_INTENT_VIEWPORT_ROTATE
          XrInputMode.LOCATION_IN_SPACE_XY -> relativeMoveEvent.intent = RelativeMoveEvent.Intent.XR_MOVE_EVENT_INTENT_VIEWPORT_PAN
          XrInputMode.LOCATION_IN_SPACE_Z -> relativeMoveEvent.intent = RelativeMoveEvent.Intent.XR_MOVE_EVENT_INTENT_VIEWPORT_ZOOM
          else -> throw Error("Internal error") // Unreachable due to the !isMouseUsedForNavigation check above.
        }
        xrInputEvent.clear()
        xrInputEvent.setMoveEvent(relativeMoveEvent)
        inputEvent.clear()
        inputEvent.setXrInputEvent(xrInputEvent)
        sendInputEvent(inputEvent.build())
      }
    }
    event.consume()
    return true
  }

  /**
   * Notifies the controller that the mouse was moved.
   * Returns true if the input event has been consumed.
   */
  @UiThread
  fun mouseMoved(event: MouseEvent): Boolean {
    return false
  }

  /**
   * Notifies the controller that the mouse wheel was moved.
   * Returns true if the input event has been consumed.
   */
  @UiThread
  fun mouseWheelMoved(event: MouseWheelEvent): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
      return false
    }
    relativeMoveEvent.setRelX(0)
    // Change the sign of wheelRotation because the direction of the mouse wheel rotation is opposite between AWT and Android.
    val delta = -event.wheelRotation.scaled(MOUSE_WHEEL_NAVIGATION_FACTOR * mouseScaleFactor)
    relativeMoveEvent.setRelY(delta)
    relativeMoveEvent.intent = RelativeMoveEvent.Intent.XR_MOVE_EVENT_INTENT_VIEWPORT_ZOOM
    xrInputEvent.clear()
    xrInputEvent.setMoveEvent(relativeMoveEvent)
    inputEvent.clear()
    inputEvent.setXrInputEvent(xrInputEvent)
    sendInputEvent(inputEvent.build())
    event.consume()
    return true
  }

  override fun dispose() {
  }

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
      VK_RIGHT, VK_KP_RIGHT -> 1 shl NavigationKey.ROTATE_RIGHT.ordinal
      VK_LEFT, VK_KP_LEFT -> 1 shl NavigationKey.ROTATE_LEFT.ordinal
      VK_UP, VK_KP_UP -> 1 shl NavigationKey.ROTATE_UP.ordinal
      VK_DOWN, VK_KP_DOWN -> 1 shl NavigationKey.ROTATE_DOWN.ordinal
      VK_PAGE_UP -> 1 shl NavigationKey.ROTATE_RIGHT_UP.ordinal
      VK_PAGE_DOWN -> 1 shl NavigationKey.ROTATE_RIGHT_UP.ordinal
      VK_HOME -> 1 shl NavigationKey.ROTATE_LEFT_UP.ordinal
      VK_END -> 1 shl NavigationKey.ROTATE_LEFT_UP.ordinal
      else -> 0
    }
  }

  private fun computeNavigationMask(pressedKeysMask: Int): Int {
    var mask = pressedKeysMask and ((1 shl NavigationKey.ROTATE_RIGHT_UP.ordinal) - 1)
    if (pressedKeysMask and (1 shl NavigationKey.ROTATE_RIGHT_UP.ordinal) != 0) {
      mask = mask or (1 shl NavigationKey.ROTATE_RIGHT.ordinal) or (1 shl NavigationKey.ROTATE_UP.ordinal)
    }
    if (pressedKeysMask and (1 shl NavigationKey.ROTATE_RIGHT_DOWN.ordinal) != 0) {
      mask = mask or (1 shl NavigationKey.ROTATE_RIGHT.ordinal) or (1 shl NavigationKey.ROTATE_DOWN.ordinal)
    }
    if (pressedKeysMask and (1 shl NavigationKey.ROTATE_LEFT_UP.ordinal) != 0) {
      mask = mask or (1 shl NavigationKey.ROTATE_LEFT.ordinal) or (1 shl NavigationKey.ROTATE_UP.ordinal)
    }
    if (pressedKeysMask and (1 shl NavigationKey.ROTATE_LEFT_DOWN.ordinal) != 0) {
      mask = mask or (1 shl NavigationKey.ROTATE_LEFT.ordinal) or (1 shl NavigationKey.ROTATE_DOWN.ordinal)
    }
    return mask
  }

  private fun sendKeyUpdate(keyMask: Int) {
    navigationEvent.clear()
    var mask = keyMask
    var key = 0
    while (mask != 0) {
      if (mask and 1 != 0) {
        when (key) {
          NavigationKey.MOVE_RIGHT.ordinal -> navigationEvent.setRightHeld(true)
          NavigationKey.MOVE_LEFT.ordinal -> navigationEvent.setLeftHeld(true)
          NavigationKey.MOVE_UP.ordinal -> navigationEvent.setUpHeld(true)
          NavigationKey.MOVE_DOWN.ordinal -> navigationEvent.setDownHeld(true)
          NavigationKey.MOVE_FORWARD.ordinal -> navigationEvent.setForwardHeld(true)
          NavigationKey.MOVE_BACKWARD.ordinal -> navigationEvent.setBackwardHeld(true)
          NavigationKey.ROTATE_RIGHT.ordinal -> navigationEvent.setRotateRightHeld(true)
          NavigationKey.ROTATE_LEFT.ordinal -> navigationEvent.setRotateLeftHeld(true)
          NavigationKey.ROTATE_UP.ordinal -> navigationEvent.setRotateUpHeld(true)
          NavigationKey.ROTATE_DOWN.ordinal -> navigationEvent.setRotateDownHeld(true)
        }
      }
      mask = mask ushr 1
      key++
    }
    xrInputEvent.clear()
    xrInputEvent.setNavEvent(navigationEvent)
    inputEvent.clear()
    inputEvent.setXrInputEvent(xrInputEvent)
    sendInputEvent(inputEvent.build())
  }

  private fun isMouseUsedForNavigation(inputMode: XrInputMode): Boolean {
    return when (inputMode) {
      XrInputMode.VIEW_DIRECTION, XrInputMode.LOCATION_IN_SPACE_XY, XrInputMode.LOCATION_IN_SPACE_Z -> true
      else -> false
    }
  }

  private fun sendInputEvent(inputEvent: InputEvent) {
    emulator.getOrCreateInputEventSender().onNext(inputEvent)
  }

  companion object {
    fun getInstance(project: Project, emulator: EmulatorController): EmulatorXrInputController =
        project.service<EmulatorXrInputControllerService>().getXrInputController(emulator)
  }

  private enum class NavigationKey {
    MOVE_FORWARD,      // W
    MOVE_LEFT,         // A
    MOVE_BACKWARD,     // S
    MOVE_RIGHT,        // D
    MOVE_DOWN,         // Q
    MOVE_UP,           // E
    ROTATE_RIGHT,      // Right arrow
    ROTATE_LEFT,       // Left arrow
    ROTATE_UP,         // Up arrow
    ROTATE_DOWN,       // Down arrow
    ROTATE_RIGHT_UP,   // Page Up
    ROTATE_RIGHT_DOWN, // Page Down
    ROTATE_LEFT_UP,    // Home
    ROTATE_LEFT_DOWN,  // End
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

@Service(Service.Level.PROJECT)
internal class EmulatorXrInputControllerService(project: Project): Disposable {

  private val xrControllers = ConcurrentMap<EmulatorController, EmulatorXrInputController>()
  private val hardwareInputStateStorage = project.service<HardwareInputStateStorage>()

  fun getXrInputController(emulator: EmulatorController): EmulatorXrInputController {
    return xrControllers.computeIfAbsent(emulator) {
      Disposer.register(emulator) {
        xrControllers.remove(emulator)
      }
      val emulatorXrInputController = EmulatorXrInputController(emulator)
      if (emulatorXrInputController.inputMode == XrInputMode.HARDWARE) {
        hardwareInputStateStorage.setHardwareInputEnabled(DeviceId.ofEmulator(emulator.emulatorId), true)
      }
      return@computeIfAbsent emulatorXrInputController
    }
  }

  override fun dispose() {
    xrControllers.clear()
  }
}