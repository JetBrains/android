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
package com.android.tools.idea.streaming.emulator.actions

import com.android.emulator.control.ParameterValue
import com.android.emulator.control.PhysicalModelValue
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.EventQueue

/**
 * Rotates the emulator left or right.
 */
sealed class EmulatorRotateAction(
  private val rotationQuadrants: Int,
) : AbstractEmulatorAction(configFilter = { it.hasOrientationSensors && !it.isWearOs }) {

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorController = getEmulatorController(event) ?: return
    val emulatorView = getEmulatorView(event) ?: return
    val rotationQuadrants = (emulatorView.displayOrientationQuadrants + rotationQuadrants) and 0x03
    val angle = when (rotationQuadrants) {
      1 -> 90F
      2 -> -180F
      3 -> -90F
      else -> 0F
    }
    val parameters = ParameterValue.newBuilder()
      .addData(0F)
      .addData(0F)
      .addData(angle)
    val rotationModel = PhysicalModelValue.newBuilder()
      .setTarget(PhysicalModelValue.PhysicalType.ROTATION)
      .setValue(parameters)
      .build()
    emulatorController.setPhysicalModel(rotationModel, object : EmptyStreamObserver<Empty>() {
      override fun onCompleted() {
        EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
          emulatorView.displayOrientationQuadrants = rotationQuadrants
        }
      }
    })
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    // Rotation is disabled if the device has more than one display.
    val presentation = event.presentation
    if (presentation.isVisible && getNumberOfDisplays(event) > 1) {
      presentation.isVisible = false
    }
  }

  class Left : EmulatorRotateAction(1)
  class Right : EmulatorRotateAction(3)
}