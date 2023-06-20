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
package com.android.tools.idea.actions

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.idea.serverflags.protos.Survey
import com.android.tools.idea.stats.FeatureSurveys
import com.android.tools.idea.stats.createDialog
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent

class FeatureSurveyNotificationAction(val name: String, val survey: Survey) : NotificationAction("Take survey") {
  override fun actionPerformed(e: AnActionEvent, notification: Notification) {
    val dialog = createDialog(survey)
    dialog.show()
    notification.expire()
  }
}