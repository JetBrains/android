/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.sdklib.deviceprovisioner.DeviceType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import java.nio.file.Files
import java.nio.file.Path

/** Displays a popup menu of available environments for AI Glasses. */
internal class EmulatorEnvironmentActionGroup : DefaultActionGroup(), DumbAware {

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    presentation.isVisible = EmulatorEnvironmentAction.emulatorSupported && getEmulatorConfig(event)?.deviceType == DeviceType.AI_GLASSES
    presentation.isEnabled = presentation.isVisible && isEmulatorConnected(event)
  }

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val children = super.getChildren(event)
    val recentFiles = EmulatorEnvironmentAction.getRecentFiles().map { Path.of(it) }.filter { Files.isRegularFile(it) }
    if (recentFiles.isEmpty()) {
      return children
    }
    val result = children.toMutableList()
    result.add(Separator("Recent Environments"))
    for (file in recentFiles) {
      result.add(EmulatorEnvironmentAction.RecentCustom(file))
    }
    return result.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
