/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.actions

import com.android.tools.idea.assistant.AssistActionHandler
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.device.settings.DeviceMirroringSettingsUi
import com.android.tools.idea.whatsnew.assistant.WhatsNewMetricsTracker
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

/**
 * Opens the Device Mirroring settings page.
 */
class OpenDeviceMirroringSettingsAction : AssistActionHandler {

  override fun getId(): String = ACTION_KEY

  override fun handleAction(actionData: ActionData, project: Project) {
    WhatsNewMetricsTracker.getInstance().clickActionButton(project, ACTION_KEY)
    ShowSettingsUtil.getInstance().showSettingsDialog(project, DeviceMirroringSettingsUi::class.java)
  }

  companion object {
    private const val ACTION_KEY = "device.mirroring.open.settings"
  }
}