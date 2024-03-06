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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiAction
import com.android.tools.idea.run.deployment.Heading
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator

internal class ActionGroupSection(val headingActionId: String?, val actions: List<AnAction>)

internal fun DefaultActionGroup.addSection(section: ActionGroupSection) {
  section.headingActionId
    ?.let { ActionManager.getInstance().getAction(it) }
    ?.let { add(Separator(it.templateText)) }
  addAll(section.actions)
}

internal fun createActionGroup(vararg sections: ActionGroupSection): DefaultActionGroup =
  DefaultActionGroup().apply {
    val presentSections = sections.filter { it.actions.isNotEmpty() }
    presentSections.firstOrNull()?.let { addSection(it) }
    for (section in presentSections.drop(1)) {
      addSeparator()
      addSection(section)
    }
  }

internal fun createDeviceSelectorActionGroup(
  devices: List<DeploymentTargetDevice>
): DefaultActionGroup {
  val actionManager = ActionManager.getInstance()
  return createActionGroup(
    ActionGroupSection(
      Heading.RUNNING_DEVICES_ID,
      devices.filter { it.isConnected }.map { SelectDeviceAction(it) },
    ),
    ActionGroupSection(
      Heading.AVAILABLE_DEVICES_ID,
      devices
        .filterNot { it.isConnected }
        .map {
          when {
            it.snapshots.isNotEmpty() -> SnapshotActionGroup(it)
            else -> SelectDeviceAction(it)
          }
        },
    ),
    ActionGroupSection(
      null,
      listOfNotNull(
        actionManager.getAction(SelectMultipleDevicesAction.ID),
        actionManager.getAction(PairDevicesUsingWiFiAction.ID),
        actionManager.getAction("Android.DeviceManager"),
      ),
    ),
    ActionGroupSection(
      null,
      listOfNotNull(actionManager.getAction("DeveloperServices.ConnectionAssistant")),
    ),
  )
}
