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
package com.android.tools.idea.streaming.device.actions

import com.android.tools.idea.streaming.device.DeviceState.Property.PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP
import com.android.tools.idea.streaming.device.FoldingState
import com.android.tools.idea.streaming.device.RequestDeviceStateMessage
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Changes a folding pose of a foldable physical device.
 * Value semantics is intended for comparisons in tests.
 */
internal data class DeviceFoldingAction(val foldingState: FoldingState) : AbstractDeviceAction() {

  init {
    templatePresentation.text = foldingState.name
    templatePresentation.icon = foldingState.icon
  }

  override fun isEnabled(event: AnActionEvent): Boolean {
    val controller = getDeviceController(event) ?: return false
    return super.isEnabled(event) && controller.supportedFoldingStates.isNotEmpty() &&
           (PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP !in foldingState.systemProperties ||
            foldingState.id == controller.currentFoldingState?.id)
  }

  override fun actionPerformed(event: AnActionEvent) {
    if (PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP in foldingState.systemProperties) {
      return
    }
    val controller = getDeviceController(event) ?: return
    if (foldingState.id != controller.currentFoldingState?.id) {
      controller.setFoldingState(foldingState.id)
      val controlMessage = RequestDeviceStateMessage(foldingState.id)
      controller.sendControlMessage(controlMessage)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
