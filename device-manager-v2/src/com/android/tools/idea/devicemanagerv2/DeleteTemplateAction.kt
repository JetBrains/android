/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.android.tools.idea.deviceprovisioner.launchCatchingDeviceActionException
import com.google.wireless.android.sdk.stats.DeviceManagerEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons
import org.jetbrains.android.AndroidPluginDisposable

class DeleteTemplateAction :
  DumbAwareAction("Delete", "Delete this device", StudioIcons.Common.DELETE) {
  override fun getActionUpdateThread() = BGT

  override fun update(e: AnActionEvent) {
    if (e.deviceHandle() != null) e.presentation.isEnabledAndVisible = false
    else e.updateFromDeviceTemplateAction(DeviceTemplate::deleteAction)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceRowData = e.deviceRowData() ?: return
    if (e.deviceHandle() != null) return
    val deviceTemplate = deviceRowData.template ?: return
    val deleteAction = deviceTemplate.deleteAction ?: return

    DeviceManagerUsageTracker.logDeviceManagerEvent(
      when {
        deviceTemplate.properties.isVirtual == true ->
          DeviceManagerEvent.EventKind.VIRTUAL_DELETE_ACTION
        else -> DeviceManagerEvent.EventKind.PHYSICAL_DELETE_ACTION
      }
    )

    val coroutineScope =
      e.deviceManagerCoroutineScope()
        ?: e.project?.let { AndroidCoroutineScope(AndroidPluginDisposable.getProjectInstance(it)) }
        ?: AndroidCoroutineScope(AndroidPluginDisposable.getApplicationInstance())
    deviceTemplate.launchCatchingDeviceActionException(coroutineScope, project = e.project) {
      deleteAction.delete()
    }
  }
}
