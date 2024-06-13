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

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class SelectMultipleDevicesAction
internal constructor(
  private val devicesService: (Project) -> DeploymentTargetDevicesService = Project::service
) : AnAction() {
  override fun update(event: AnActionEvent) {
    val project = event.project
    val presentation = event.presentation
    if (project == null) {
      presentation.setEnabledAndVisible(false)
      return
    }
    presentation.setEnabledAndVisible(
      devicesService(project).loadedDevicesOrNull()?.isNotEmpty() ?: false
    )
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    SelectMultipleDevicesDialog(requireNotNull(event.project)).showAndGet()
  }

  companion object {
    const val ID = "SelectMultipleDevices"
  }
}
