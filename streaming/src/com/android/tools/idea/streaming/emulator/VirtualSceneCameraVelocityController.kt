/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.emulator.control.Velocity
import com.intellij.openapi.Disposable

/**
 * Controller that changes virtual camera velocity in response to pressing and releasing WASDQE keys.
 * ```
 *   D - forward along X axis
 *   A - backward along X axis
 *   E - forward along Y axis
 *   Q - backward along Y axis
 *   S - forward along Z axis
 *   W - backward along Z axis
 * ```
 * For AZERTY keyboard the keys are ZQSDAE.
 */
internal class VirtualSceneCameraVelocityController(
  private val emulator: EmulatorController,
  private val controlKeys: String
) : Disposable {

  private var pressedKeysMask = 0
  private val virtualSceneCameraVelocity = Velocity.newBuilder()

  /**
   * Stops the camera movement and releases all keys.
   */
  override fun dispose() {
    // Stop the camera movement and release all keys.
    pressedKeysMask = 0
    if (virtualSceneCameraVelocity.x != 0F || virtualSceneCameraVelocity.y != 0F || virtualSceneCameraVelocity.z != 0F) {
      virtualSceneCameraVelocity.clear()
      emulator.setVirtualSceneCameraVelocity(Velocity.getDefaultInstance())
    }
  }

  /**
   * Notifies the controller that a key was pressed.
   */
  fun keyPressed(keyCode: Int) {
    val mask = keyToMask(keyCode)
    val newPressed = pressedKeysMask or mask
    if (pressedKeysMask != newPressed) {
      pressedKeysMask = newPressed
      updateCameraVelocity(mask, CAMERA_VELOCITY_UNIT)
    }
  }

  /**
   * Notifies the controller that a key was released.
   */
  fun keyReleased(keyCode: Int) {
    val mask = keyToMask(keyCode)
    val newPressed = pressedKeysMask and mask.inv()
    if (pressedKeysMask != newPressed) {
      pressedKeysMask = newPressed
      updateCameraVelocity(mask, -CAMERA_VELOCITY_UNIT)
    }
  }

  private fun keyToMask(keyCode: Int): Int {
    val index = controlKeys.indexOf(keyCode.toChar())
    return if (index >= 0) 1 shl index else 0
  }

  private fun updateCameraVelocity(mask: Int, deltaVelocity: Float) {
    when (mask) {
      0x08 -> virtualSceneCameraVelocity.x += deltaVelocity // D
      0x02 -> virtualSceneCameraVelocity.x -= deltaVelocity // A
      0x20 -> virtualSceneCameraVelocity.y += deltaVelocity // E
      0x10 -> virtualSceneCameraVelocity.y -= deltaVelocity // Q
      0x04 -> virtualSceneCameraVelocity.z += deltaVelocity // S
      0x01 -> virtualSceneCameraVelocity.z -= deltaVelocity // W
      else -> throw IllegalArgumentException()
    }
    emulator.setVirtualSceneCameraVelocity(virtualSceneCameraVelocity.build())
  }
}

private const val CAMERA_VELOCITY_UNIT = 1F