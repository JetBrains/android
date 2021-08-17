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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.UpdatePluginHyperlink
import com.android.tools.idea.gradle.project.sync.issues.processor.GradlePluginInfo
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.project.messages.MessageType
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class OutOfDateThirdPartyPluginIssueReporter : SimpleDeduplicatingSyncIssueReporter() {
  override fun getCustomLinks(
    project: Project,
    syncIssues: List<IdeSyncIssue>,
    affectedModules: List<Module>,
    buildFileMap: Map<Module, VirtualFile>
  ): List<NotificationHyperlink> {
    val pluginToVersionMap = syncIssues.mapNotNull { it.issueData() }.distinctBy { it.name to it.group }.associate {
      GradlePluginInfo(it.name, it.group) to it.minimumVersion
    }

    return if (pluginToVersionMap.isEmpty()) emptyList() else listOf(UpdatePluginHyperlink(pluginToVersionMap))
  }

  override fun setupNotificationData(
    project: Project,
    syncIssues: List<IdeSyncIssue>,
    affectedModules: List<Module>,
    buildFileMap: Map<Module, VirtualFile>,
    type: MessageType
  ): NotificationData {
    val notificationData = super.setupNotificationData(project, syncIssues, affectedModules, buildFileMap, type)

    if (syncIssues.isEmpty()) return notificationData

    val IdeSyncIssue = syncIssues[0]
    val messageParts = IdeSyncIssue.message.split(":", limit = 2)
    val messageStem = if (messageParts.isEmpty()) "Some plugins require updates" else messageParts[0]

    val paths = syncIssues.flatMap { issue -> issue.issueData()?.violatingPaths ?: listOf() }
    if (paths.isEmpty()) return notificationData
    notificationData.message = "$messageStem:\n" + paths.joinToString("\n")

    return notificationData
  }

  override fun getSupportedIssueType(): Int = IdeSyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD

  override fun getDeduplicationKey(issue: IdeSyncIssue): Any = issue.issueData()?.displayName ?: issue

  /**
   * Creates a IssueData object by splitting up the payload of the SyncIssues data field,
   * this field is populated by the Android Gradle Plugin and has the following format:
   *   pluginDisplayName;pluginGroup;pluginName;minimumVersion;violatingPaths]
   * Add parts are string apart from violatingPaths which is a list in the form of:
   *   [path1, path2, path3]
   */
  private fun IdeSyncIssue.issueData(): IssueData? {
    val fields = data?.split(";", limit = 5)?.takeUnless { it.size < 5 } ?: return null
    val paths = if (fields[4].length < 2) listOf() else fields[4].substring(1, fields[4].length - 1).split(",")

    return IssueData(
      fields[0],
      fields[1],
      fields[2],
      fields[3],
      paths
    )
  }

  data class IssueData(
    val displayName: String,
    val group: String,
    val name: String,
    val minimumVersion: String,
    val violatingPaths: List<String>
  )
}