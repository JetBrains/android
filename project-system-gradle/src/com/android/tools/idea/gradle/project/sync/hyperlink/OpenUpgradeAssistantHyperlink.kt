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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink
import com.android.tools.idea.gradle.project.upgrade.AssistantInvoker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.project.Project

class OpenUpgradeAssistantHyperlink: SyncIssueNotificationHyperlink(
  "openUpgradeAssistant",
  "Open AGP Upgrade Assistant",
  AndroidStudioEvent.GradleSyncQuickFix.OPEN_UPGRADE_ASSISTANT_HYPERLINK
) {

  override fun execute(project: Project) {
    showUpgradeAssistant(project)
  }

  private fun showUpgradeAssistant(project: Project) {
    project.getService(AssistantInvoker::class.java).performRecommendedPluginUpgrade(project)
  }
}