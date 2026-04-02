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

import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.idea.deviceprovisioner.EditableDeviceHandle
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.android.tools.idea.deviceprovisioner.runCatchingDeviceActionExceptionBlocking
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_EDIT_ACTION
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class EditDeviceAction : DumbAwareAction("Edit", "Edit this device", AllIcons.Actions.Edit) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.deviceHandle() is EditableDeviceHandle
  }

  override fun actionPerformed(e: AnActionEvent) {
    val handle = e.deviceHandle() as? EditableDeviceHandle ?: return

    DeviceManagerUsageTracker.logDeviceManagerEvent(VIRTUAL_EDIT_ACTION)

    runCatchingDeviceActionExceptionBlocking(e.project, handle.state.properties.title) {
      handle.edit(e.project, e.componentToRestoreFocusTo())
    }
  }
}
