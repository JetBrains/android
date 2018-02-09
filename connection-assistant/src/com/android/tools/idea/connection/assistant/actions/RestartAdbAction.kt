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

import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.assistant.AssistActionHandler
import com.android.tools.idea.assistant.datamodel.ActionData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ConnectionAssistantEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

/**
 * Used in Connection Assistant, allows user to restart ADB to scan for new connected Android devices.
 */
class RestartAdbAction : AssistActionHandler {
  companion object {
    @JvmStatic val ACTION_ID = "connection.restart.adb"
  }

  override fun getId(): String = ACTION_ID

  override fun handleAction(actionData: ActionData, project: Project) {
    val adb = AndroidDebugBridge.getBridge() ?: return
    ApplicationManager.getApplication().executeOnPooledThread { adb.restart() }

    UsageTracker.getInstance()
        .log(AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.CONNECTION_ASSISTANT_EVENT)
            .setConnectionAssistantEvent(ConnectionAssistantEvent.newBuilder()
                .setType(ConnectionAssistantEvent.ConnectionAssistantEventType.RESTART_ADB_CLICKED)))
  }
}
