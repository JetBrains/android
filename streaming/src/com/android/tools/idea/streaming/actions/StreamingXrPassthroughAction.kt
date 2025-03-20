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
package com.android.tools.idea.streaming.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.streaming.xr.AbstractXrInputController.Companion.UNKNOWN_PASSTHROUGH_COEFFICIENT
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.launch

/** Toggles passthrough for a virtual XR headset. */
class StreamingXrPassthroughAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    val xrController = getXrInputController(event) ?: return false
    return xrController.passthroughCoefficient > 0
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    getXrInputController(event)?.apply {
      createCoroutineScope().launch {
        try {
          setPassthrough(if (state) 1f else 0f)
        }
        catch (e: Exception) {
          thisLogger().warn("Unable to set passthrough", e)
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    presentation.isVisible = getDeviceType(event) == DeviceType.XR
    presentation.isEnabled = isEnabled(event)
  }

  private fun isEnabled(event: AnActionEvent): Boolean {
    val xrController = getXrInputController(event) ?: return false
    // TODO Disable in transit.
    return xrController.passthroughCoefficient != UNKNOWN_PASSTHROUGH_COEFFICIENT
  }
}