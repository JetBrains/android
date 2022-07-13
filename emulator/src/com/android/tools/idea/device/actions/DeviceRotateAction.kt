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
package com.android.tools.idea.device.actions

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.device.SetDeviceOrientationMessage
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Rotates device left or right.
 */
internal sealed class DeviceRotateAction(
  private val rotationQuadrants: Int,
) : AbstractDeviceAction(configFilter = { it.hasOrientationSensors && !it.isWatch }) {

  @UiThread
  override fun actionPerformed(event: AnActionEvent) {
    val deviceController = getDeviceController(event) ?: return
    val deviceView = getDeviceView(event) ?: return
    val orientation = (deviceView.displayOrientationQuadrants + rotationQuadrants) and 0x03
    val controlMessage = SetDeviceOrientationMessage(orientation)
    deviceController.sendControlMessage(controlMessage)
  }


  class Left : DeviceRotateAction(1)
  class Right : DeviceRotateAction(3)
}