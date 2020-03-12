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
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.PhysicalModelValue
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlin.math.roundToInt

/**
 * Common superclass of [EmulatorRotateLeftAction] and [EmulatorRotateRightAction].
 */
abstract class EmulatorRotateAction : AbstractEmulatorAction() {
  @UiThread
  fun rotate(event: AnActionEvent, degrees: Float) {
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    emulatorController.getPhysicalModel(PhysicalModelValue.PhysicalType.ROTATION, Rotator(emulatorController, degrees))
  }

  private class Rotator(
    val emulatorController: EmulatorController,
    val degrees: Float
  ) : DummyStreamObserver<PhysicalModelValue>() {

    override fun onNext(response: PhysicalModelValue) {
      // Rotate around z axis.
      val parameters = response.value.toBuilder()
        .setData(2, canonicalizeRotationAngle(response.value.getData(2) + degrees))
      val rotationModel = response.toBuilder().setValue(parameters).build()
      emulatorController.setPhysicalModel(rotationModel)
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
}