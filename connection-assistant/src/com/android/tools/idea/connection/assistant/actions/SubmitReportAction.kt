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

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.assistant.AssistActionHandler
import com.android.tools.idea.assistant.datamodel.ActionData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ConnectionAssistantEvent
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project

const val ACTION_ID: String = "connection.submit.report"

class SubmitReportAction : AssistActionHandler {
  override fun getId(): String = ACTION_ID

  override fun handleAction(actionData: ActionData, project: Project) {
    UsageTracker.getInstance()
        .log(AndroidStudioEvent.newBuilder()
            .setKind(AndroidStudioEvent.EventKind.CONNECTION_ASSISTANT_EVENT)
            .setConnectionAssistantEvent(ConnectionAssistantEvent.newBuilder()
                .setType(ConnectionAssistantEvent.ConnectionAssistantEventType.REPORT_ISSUE_CLICKED)))

    BrowserUtil.browse("https://issuetracker.google.com/issues/new?component=328290&template=0")
  }
}
