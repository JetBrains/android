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
import com.android.emulator.control.AngularVelocity
import com.android.emulator.control.InputEvent
import com.android.emulator.control.RotationRadian
import com.android.emulator.control.Translation
import com.android.emulator.control.Velocity
import com.android.emulator.control.XrOptions
import com.android.emulator.control.XrOptions.Environment.forNumber
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.streaming.actions.HardwareInputStateStorage
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.getNormalizedScrollAmount
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.xr.AbstractXrInputController
import com.android.tools.idea.streaming.xr.XrInputMode
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * Orchestrates mouse and keyboard input for XR devices. Keeps track of XR environment and passthrough.
 * Thread safe.
 */
internal class EmulatorXrInputController(private val emulator: EmulatorController) : AbstractXrInputController() {

  private val inputEvent = InputEvent.newBuilder()
  private val rotation = RotationRadian.newBuilder()
  private val translation = Translation.newBuilder()
  private val angularVelocity = AngularVelocity.newBuilder()
  private val velocity = Velocity.newBuilder()

  init {
    Disposer.register(emulator, this)
  }

  override suspend fun setPassthrough(passthroughCoefficient: Float) {
    suspendCancellableCoroutine { continuation ->
      val xrOptions = XrOptions.newBuilder()
        .setPassthroughCoefficient(passthroughCoefficient)
        .setEnvironment(environment?.let { forNumber(it.ordinal) })
        .build()
      emulator.setXrOptions(xrOptions, object : EmptyStreamObserver<Empty>() {
        override fun onNext(message: Empty) {
          this@EmulatorXrInputController.passthroughCoefficient = passthroughCoefficient
          ActivityTracker.getInstance().inc()
          continuation.resume(Unit)
        }

        override fun onError(t: Throwable) {
          continuation.resumeWithException(t)
        }
      })
    }
  }

  override fun sendTranslation(x: Float, y: Float, z: Float) {
    translation.deltaX = x
    translation.deltaY = y
    translation.deltaZ = z
    inputEvent.setXrHeadMovementEvent(translation)
    sendInputEvent(inputEvent.build())
  }

  @UiThread
  override fun mouseDragged(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation()) {
      return false
    }
    val referencePoint = mouseDragReferencePoint
    if (referencePoint != null) {
      val movementScale = if (inputMode == XrInputMode.VIEW_DIRECTION) ROTATION_SCALE else TRANSLATION_SCALE
      val scale = (movementScale / min(deviceDisplaySize.width, deviceDisplaySize.height) * scaleFactor).toFloat()
      val deltaX = event.x - referencePoint.x
      val deltaY = event.y - referencePoint.y
      mouseDragReferencePoint = event.point
      if (deltaX != 0 || deltaY != 0) {
        inputEvent.clear()
        when (inputMode) {
          XrInputMode.LOCATION_IN_SPACE_XY -> {
            // Direction of movement in 3D space is opposite to direction of mouse dragging.
            translation.deltaX = -deltaX * scale
            translation.deltaY = deltaY * scale // Direction of Y axis in 3D space is opposite to screen coordinates.
            translation.clearDeltaZ()
            inputEvent.setXrHeadMovementEvent(translation)
          }
          XrInputMode.LOCATION_IN_SPACE_Z -> {
            translation.clear()
            translation.deltaZ = -deltaY * scale // Dragging mouse down moves forward in 3D space.
            inputEvent.setXrHeadMovementEvent(translation)
          }
          XrInputMode.VIEW_DIRECTION -> {
            // View direction follows the direction of the mouse movement.
            rotation.x = -deltaY * scale
            rotation.y = -deltaX * scale
            inputEvent.setXrHeadRotationEvent(rotation)
          }
          else -> throw Error("Internal error") // Unreachable due to the !isMouseUsedForNavigation check above.
        }
        sendInputEvent(inputEvent.build())
      }
    }
    event.consume()
    return true
  }

  @UiThread
  override fun mouseWheelMoved(event: MouseWheelEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation()) {
      return false
    }
    translation.clear()
    // Rotating mouse wheel forward moves the viewer forward in 3D space.
    translation.deltaZ = event.getNormalizedScrollAmount(scaleFactor).toFloat() * MOUSE_WHEEL_NAVIGATION_FACTOR
    inputEvent.clear()
    inputEvent.setXrHeadMovementEvent(translation)
    sendInputEvent(inputEvent.build())
    event.consume()
    return true
  }

  override fun sendVelocityUpdate(newMask: Int, oldMask: Int) {
    velocity.clear()
    angularVelocity.clear()
    var mask = newMask
    var key = 0
    while (mask != 0) {
      if (mask and 1 != 0) {
        if (key < NavigationKey.ROTATE_RIGHT.ordinal) {
          when (key) {
            NavigationKey.MOVE_RIGHT.ordinal -> velocity.x = VELOCITY
            NavigationKey.MOVE_LEFT.ordinal -> velocity.x = -VELOCITY
            NavigationKey.MOVE_UP.ordinal -> velocity.y = VELOCITY
            NavigationKey.MOVE_DOWN.ordinal -> velocity.y = -VELOCITY
            NavigationKey.MOVE_FORWARD.ordinal -> velocity.z = -VELOCITY
            NavigationKey.MOVE_BACKWARD.ordinal -> velocity.z = VELOCITY
          }
        }
        else {
          when (key) {
            NavigationKey.ROTATE_RIGHT.ordinal -> angularVelocity.omegaY = -ANGULAR_VELOCITY
            NavigationKey.ROTATE_LEFT.ordinal -> angularVelocity.omegaY = ANGULAR_VELOCITY
            NavigationKey.ROTATE_UP.ordinal -> angularVelocity.omegaX = ANGULAR_VELOCITY
            NavigationKey.ROTATE_DOWN.ordinal -> angularVelocity.omegaX = -ANGULAR_VELOCITY
          }
        }
      }
      mask = mask ushr 1
      key++
    }
    val differences = newMask xor oldMask
    if ((differences and NavigationKey.TRANSLATION_MASK) != 0) {
      inputEvent.clear()
      inputEvent.setXrHeadVelocityEvent(velocity)
      sendInputEvent(inputEvent.build())
    }
    if ((differences and NavigationKey.ROTATION_MASK) != 0) {
      inputEvent.clear()
      inputEvent.setXrHeadAngularVelocityEvent(angularVelocity)
      sendInputEvent(inputEvent.build())
    }
  }

  override fun dispose() {
  }

  private fun sendInputEvent(inputEvent: InputEvent) {
    emulator.getOrCreateInputEventSender().onNext(inputEvent)
  }

  companion object {
    fun getInstance(project: Project, emulator: EmulatorController): EmulatorXrInputController =
        project.service<EmulatorXrInputControllerService>().getXrInputController(emulator)
  }
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
