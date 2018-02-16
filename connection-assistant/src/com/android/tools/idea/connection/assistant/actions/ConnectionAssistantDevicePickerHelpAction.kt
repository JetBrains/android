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
package com.android.tools.idea.connection.assistant.actions

import com.android.tools.idea.actions.DevicePickerHelpAction
import com.android.tools.idea.assistant.OpenAssistSidePanelAction
import com.android.tools.idea.connection.assistant.ConnectionAssistantBundleCreator
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ConnectionAssistantDevicePickerHelpAction : DevicePickerHelpAction() {
  override fun actionPerformed(e: AnActionEvent?) {
    if (ConnectionAssistantBundleCreator.isAssistantEnabled()) {
      val action = OpenAssistSidePanelAction()
      action.openWindow(ConnectionAssistantBundleCreator.BUNDLE_ID, e?.project)
    } else {
      super.actionPerformed(e)
    }
  }

  override fun closeDialog() = true
}

