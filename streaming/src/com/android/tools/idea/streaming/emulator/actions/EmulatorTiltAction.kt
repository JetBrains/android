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
import com.android.emulator.control.PhysicalModelValue.PhysicalType.WRIST_TILT
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Triggers the tilt sensor on an Android Wear virtual device.
 */
internal class EmulatorTiltAction : AbstractEmulatorAction(configFilter = { it.isWearOs && it.api >= 28 }) {

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorController = getEmulatorController(event) ?: return
    val physicalModelValue = PhysicalModelValue.newBuilder()
        .setTarget(WRIST_TILT)
        .setValue(ParameterValue.newBuilder().addData(1F))
    emulatorController.setPhysicalModel(physicalModelValue.build())
  }
}