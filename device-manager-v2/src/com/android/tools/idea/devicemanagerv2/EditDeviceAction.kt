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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.idea.deviceprovisioner.DEVICE_HANDLE_KEY
import com.google.wireless.android.sdk.stats.DeviceManagerEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.launch

/** Invokes the DeviceHandle's edit action, if available. */
class EditDeviceAction : AnAction("Edit", "Edit this device", AllIcons.Actions.Edit) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val handle = DEVICE_HANDLE_KEY.getData(e.dataContext)
    when (val editAction = handle?.editAction) {
      null -> e.presentation.isEnabledAndVisible = false
      else -> e.presentation.isEnabled = editAction.presentation.value.enabled
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val handle = DEVICE_HANDLE_KEY.getData(e.dataContext) ?: return

    DeviceManagerUsageTracker.logDeviceManagerEvent(
      DeviceManagerEvent.EventKind.VIRTUAL_EDIT_ACTION
    )

    handle.scope.launch { handle.editAction?.edit() }
  }
}
