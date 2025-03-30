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
package com.android.tools.idea.whatsnew.assistant.actions

import com.android.tools.idea.appinspection.ide.ui.AppInspectionToolWindow
import com.android.tools.idea.assistant.AssistActionHandler
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.whatsnew.assistant.WhatsNewMetricsTracker
import com.intellij.openapi.project.Project

class AppInspectionShowAction : AssistActionHandler {
  companion object {
    const val ACTION_KEY = "app.inspection.show"
  }

  override fun getId(): String = ACTION_KEY
  override fun handleAction(actionData: ActionData, project: Project) {
    WhatsNewMetricsTracker.getInstance().clickActionButton(project, ACTION_KEY)
    AppInspectionToolWindow.show(project)
  }
}