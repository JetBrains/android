/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.emulator.control.PhysicalModelValue.PhysicalType
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration.PostureDescriptor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Changes a folding pose of a foldable physical device.
 * Value semantics is intended for comparisons in tests.
 */
internal data class EmulatorFoldingAction(val posture: PostureDescriptor) : AbstractEmulatorAction() {

  init {
    templatePresentation.text = posture.displayName
    templatePresentation.icon = posture.icon
  }

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorView = getEmulatorView(event) ?: return
    if (posture != emulatorView.currentPosture) {
      val emulator = emulatorView.emulator
      val type = if (posture.valueType == PostureDescriptor.ValueType.HINGE_ANGLE) PhysicalType.HINGE_ANGLE0 else PhysicalType.ROLLABLE0
      val physicalModelValue = PhysicalModelValue.newBuilder()
        .setTarget(type)
        .setValue(ParameterValue.newBuilder().addData(getPostureValue(posture, emulator.emulatorConfig.postures).toFloat()))
      emulator.setPhysicalModel(physicalModelValue.build())
    }
  }

  /**
   * Returns the minimum or the maximum allowed posture value if it belongs to the [posture]'s value range.
   * Otherwise, returns the middle of the [posture]'s value range.
   */
  private fun getPostureValue(posture: PostureDescriptor, postures: List<PostureDescriptor>): Double {
    var minValue = Double.MAX_VALUE
    var maxValue = Double.MIN_VALUE
    for (p in postures) {
      if (minValue > p.minValue) {
        minValue = p.minValue
      }
      if (maxValue < p.maxValue) {
        maxValue = p.maxValue
      }
    }
    return when {
      posture.minValue == minValue -> minValue
      posture.maxValue == maxValue -> maxValue
      else -> (posture.minValue + posture.maxValue) / 2
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
