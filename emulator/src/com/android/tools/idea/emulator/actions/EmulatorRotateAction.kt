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
package com.android.tools.idea.emulator.actions

import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.ParameterValue
import com.android.emulator.control.PhysicalModelValue
import com.android.emulator.control.Rotation.SkinRotation
import com.android.tools.idea.emulator.EmptyStreamObserver
import com.android.tools.idea.protobuf.Empty
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlin.math.roundToInt

/**
 * Common superclass of [EmulatorRotateLeftAction] and [EmulatorRotateRightAction].
 */
abstract class EmulatorRotateAction : AbstractEmulatorAction() {
  @UiThread
  fun rotate(event: AnActionEvent, degrees: Float) {
    val emulatorController = getEmulatorController(event) ?: return
    val emulatorView = getEmulatorView(event)
    val rotation = emulatorView?.displayRotation ?: return
    val angle = canonicalizeRotationAngle(rotation.ordinal * 90F + degrees)
    val parameters = ParameterValue.newBuilder()
      .addData(0F)
      .addData(0F)
      .addData(angle)
    val rotationModel = PhysicalModelValue.newBuilder()
      .setTarget(PhysicalModelValue.PhysicalType.ROTATION)
      .setValue(parameters)
      .build()
    emulatorController.setPhysicalModel(rotationModel, object: EmptyStreamObserver<Empty>() {
      override fun onNext(response: Empty) {
        emulatorView.displayRotation = SkinRotation.forNumber(((angle / 90).toInt() + 4) % 4)
      }
    })
  }

  /**
   * Rounds the given angle to a multiple of 90 degrees and puts it in the [-180, 180) interval.
   */
  private fun canonicalizeRotationAngle(angleDegrees: Float): Float {
    val angle = (angleDegrees / 90).roundToInt() * 90F
    return when {
      angle < -180F -> angle + 360
      angle >= 180F -> angle - 360
      else -> angle
    }
  }
}