/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * When a device has multiple targets (currently only the case for devices with snapshots), it is
 * represented by this ActionGroup in the device selector, which expands to show the various target
 * options.
 */
internal class SnapshotActionGroup(val device: DeploymentTargetDevice) : ActionGroup() {
  init {
    isPopup = true
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    return device.targets.map { SelectTargetAction(it) }.toTypedArray()
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    presentation.setIcon(device.icon)
    presentation.setText(device.name, false)
  }
}
