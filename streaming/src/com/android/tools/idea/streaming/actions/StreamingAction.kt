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
package com.android.tools.idea.streaming.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.device.actions.getDeviceClient
import com.android.tools.idea.streaming.device.xr.DeviceXrInputController
import com.android.tools.idea.streaming.emulator.actions.getEmulatorController
import com.android.tools.idea.streaming.emulator.xr.EmulatorXrInputController
import com.android.tools.idea.streaming.xr.AbstractXrInputController
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.content.Content

internal abstract class AbstractStreamingAction<T : AnAction, U : AnAction>(
  protected val virtualDeviceAction: T,
  protected val physicalDeviceAction: U,
) : AnAction(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return if (virtualDeviceAction.actionUpdateThread == BGT && physicalDeviceAction.actionUpdateThread == BGT) BGT else EDT
  }

  override fun update(event: AnActionEvent) {
    if (getEmulatorController(event) == null) {
      physicalDeviceAction.update(event)
    }
    else {
      virtualDeviceAction.update(event)
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    if (getEmulatorController(event) == null) {
      physicalDeviceAction.actionPerformed(event)
    }
    else {
      virtualDeviceAction.actionPerformed(event)
    }
  }

  fun isDelegatingTo(action: AnAction): Boolean =
      action == virtualDeviceAction || action == physicalDeviceAction
}

internal open class StreamingAction(virtualDeviceAction: AnAction, physicalDeviceAction: AnAction) :
    AbstractStreamingAction<AnAction, AnAction>(virtualDeviceAction, physicalDeviceAction)

internal fun getDisplayView(event: AnActionEvent): AbstractDisplayView? =
    event.getData(DISPLAY_VIEW_KEY)

internal fun getDeviceType(event: AnActionEvent): DeviceType? =
    getDisplayView(event)?.deviceType

internal fun getXrInputController(event: AnActionEvent): AbstractXrInputController? {
  if (getDeviceType(event) != DeviceType.XR) {
    return null
  }
  val project = event.project ?: return null
  return getEmulatorController(event)?.let { emulatorController -> EmulatorXrInputController.getInstance(project, emulatorController) } ?:
      getDeviceClient(event)?.let { deviceClient -> DeviceXrInputController.getInstance(project, deviceClient) }
}

internal val AnActionEvent.toolWindowContents: List<Content>
  get() {
    val toolWindow = getData(PlatformDataKeys.TOOL_WINDOW) ?: return emptyList()
    val contentManager = toolWindow.contentManagerIfCreated ?: return emptyList()
    return contentManager.contentsRecursively
  }

