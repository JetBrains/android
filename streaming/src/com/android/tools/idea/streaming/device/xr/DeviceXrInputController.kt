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
package com.android.tools.idea.streaming.device.xr

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.streaming.actions.HardwareInputStateStorage
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.getNormalizedScrollAmount
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.XrAngularVelocityMessage
import com.android.tools.idea.streaming.device.XrRotationMessage
import com.android.tools.idea.streaming.device.XrTranslationMessage
import com.android.tools.idea.streaming.device.XrVelocityMessage
import com.android.tools.idea.streaming.xr.AbstractXrInputController
import com.android.tools.idea.streaming.xr.XrInputMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.ktor.util.collections.ConcurrentMap
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.min

/**
 * Orchestrates mouse and keyboard input for XR devices. Keeps track of XR environment and passthrough.
 * Thread safe.
 */
internal class DeviceXrInputController(private val deviceClient: DeviceClient) : AbstractXrInputController() {

  init {
    Disposer.register(deviceClient, this)
  }

  override suspend fun setPassthrough(passthroughCoefficient: Float) {
    // TODO: Implement when IXrSimulatedInputManager supports it.
    thisLogger().error("This operation is not implemented yet")
  }

  @UiThread
  override fun mouseDragged(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
      return false
    }
    val referencePoint = mouseDragReferencePoint
    if (referencePoint != null) {
      val movementScale = if (inputMode == XrInputMode.VIEW_DIRECTION) ROTATION_SCALE else TRANSLATION_SCALE
      val scale = movementScale * scaleFactor.toFloat() / min(deviceDisplaySize.width, deviceDisplaySize.height)
      val deltaX = event.x - referencePoint.x
      val deltaY = event.y - referencePoint.y
      mouseDragReferencePoint = event.point
      if (deltaX != 0 || deltaY != 0) {
        val controlMessage = when (inputMode) {
          XrInputMode.LOCATION_IN_SPACE_XY -> {
            // Direction of movement in 3D space is opposite to direction of mouse dragging.
            // Direction of Y axis in 3D space is opposite to screen coordinates.
            XrTranslationMessage(-deltaX * scale, deltaY * scale, 0f)
          }
          XrInputMode.LOCATION_IN_SPACE_Z -> {
            XrTranslationMessage(0f, 0f, deltaY * scale) // Dragging mouse down moves forward in 3D space.
          }
          XrInputMode.VIEW_DIRECTION -> {
            // Moving the mouse between opposite edges of the device display shifts the view direction by 180 degrees.
            // Dragging mouse down rotates the direction of view up.
            // Dragging mouse to the right rotates the direction of view to the left.
            XrRotationMessage(deltaY * scale, deltaX * scale)
          }
          else -> throw Error("Internal error") // Unreachable due to the !isMouseUsedForNavigation check above.
        }
        deviceClient.deviceController?.sendControlMessage(controlMessage)
      }
    }
    event.consume()
    return true
  }

  @UiThread
  override fun mouseWheelMoved(event: MouseWheelEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
      return false
    }
    // Rotating mouse wheel forward moves the viewer forward in 3D space.
    val deltaZ = event.getNormalizedScrollAmount(scaleFactor).toFloat() * MOUSE_WHEEL_NAVIGATION_FACTOR
    val controlMessage = XrTranslationMessage(0f, 0f, deltaZ)
    deviceClient.deviceController?.sendControlMessage(controlMessage)
    event.consume()
    return true
  }

  override fun sendVelocityUpdate(newMask: Int, oldMask: Int) {
    var vX = 0f
    var vY = 0f
    var vZ = 0f
    var omegaX = 0f
    var omegaY = 0f
    var mask = newMask
    var key = 0
    while (mask != 0) {
      if (mask and 1 != 0) {
        if (key < NavigationKey.ROTATE_RIGHT.ordinal) {
          when (key) {
            NavigationKey.MOVE_RIGHT.ordinal -> vX = VELOCITY
            NavigationKey.MOVE_LEFT.ordinal -> vX = -VELOCITY
            NavigationKey.MOVE_UP.ordinal -> vY = VELOCITY
            NavigationKey.MOVE_DOWN.ordinal -> vY = -VELOCITY
            NavigationKey.MOVE_FORWARD.ordinal -> vZ = -VELOCITY
            NavigationKey.MOVE_BACKWARD.ordinal -> vZ = VELOCITY
          }
        }
        else {
          when (key) {
            NavigationKey.ROTATE_RIGHT.ordinal -> omegaY = -ANGULAR_VELOCITY
            NavigationKey.ROTATE_LEFT.ordinal -> omegaY = ANGULAR_VELOCITY
            NavigationKey.ROTATE_UP.ordinal -> omegaX = ANGULAR_VELOCITY
            NavigationKey.ROTATE_DOWN.ordinal -> omegaX = -ANGULAR_VELOCITY
          }
        }
      }
      mask = mask ushr 1
      key++
    }
    val differences = newMask xor oldMask
    if ((differences and NavigationKey.TRANSLATION_MASK) != 0) {
      val controlMessage = XrVelocityMessage(vX, vY, vZ)
      deviceClient.deviceController?.sendControlMessage(controlMessage)
    }
    if ((differences and NavigationKey.ROTATION_MASK) != 0) {
      val controlMessage = XrAngularVelocityMessage(omegaX, omegaY)
      deviceClient.deviceController?.sendControlMessage(controlMessage)
    }
  }

  override fun dispose() {
  }

  companion object {
    fun getInstance(project: Project, deviceClient: DeviceClient): DeviceXrInputController =
        project.service<DeviceXrInputControllerService>().getXrInputController(deviceClient)
  }
}

@Service(Service.Level.PROJECT)
internal class DeviceXrInputControllerService(project: Project): Disposable {

  private val xrControllers = ConcurrentMap<DeviceClient, DeviceXrInputController>()
  private val hardwareInputStateStorage = project.service<HardwareInputStateStorage>()

  fun getXrInputController(deviceClient: DeviceClient): DeviceXrInputController {
    return xrControllers.computeIfAbsent(deviceClient) {
      Disposer.register(deviceClient) {
        xrControllers.remove(deviceClient)
      }
      val xrInputController = DeviceXrInputController(deviceClient)
      if (xrInputController.inputMode == XrInputMode.HARDWARE) {
        hardwareInputStateStorage.setHardwareInputEnabled(DeviceId.ofPhysicalDevice(deviceClient.deviceSerialNumber), true)
      }

      return@computeIfAbsent xrInputController
    }
  }

  override fun dispose() {
    xrControllers.clear()
  }
}
