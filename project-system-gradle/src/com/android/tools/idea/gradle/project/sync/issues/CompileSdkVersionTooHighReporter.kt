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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.sync.hyperlink.SuppressUnsupportedSdkVersionHyperlink
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile


class CompileSdkVersionTooHighReporter : SimpleDeduplicatingSyncIssueReporter() {

  override fun getSupportedIssueType(): Int {
    return IdeSyncIssue.TYPE_COMPILE_SDK_VERSION_TOO_HIGH
  }

  // All issues of this type should be grouped together
  override fun getDeduplicationKey(issue: IdeSyncIssue): Any = supportedIssueType

  override fun getCustomLinks(project: Project,
                              syncIssues: MutableList<IdeSyncIssue>,
                              affectedModules: MutableList<Module>,
                              buildFileMap: MutableMap<Module, VirtualFile>): List<SyncIssueNotificationHyperlink> {
    if (syncIssues.isEmpty() || affectedModules.isEmpty()) {
      return emptyList()
    }
    return createQuickFixes(project, syncIssues)
  }

  @VisibleForTesting
  fun createQuickFixes(project: Project, syncIssues: MutableList<IdeSyncIssue>): List<SyncIssueNotificationHyperlink> {
    return listOfNotNull(
      suppressSdkQuickFix(project, syncIssues),
    )
  }

  private fun suppressSdkQuickFix(project: Project, syncIssues: MutableList<IdeSyncIssue>): SuppressUnsupportedSdkVersionHyperlink? {
    val pluginInfo = AndroidPluginInfo.find(project)
    if (pluginInfo != null) {
      val agpVersion = pluginInfo.pluginVersion
      return if (agpVersion != null && agpVersion.isAtLeast(8, 2, 0, "alpha", 7, false)) {
        syncIssues[0].data?.let {
          SuppressUnsupportedSdkVersionHyperlink(it)
        }
      } else {
        tryExtractPropertyFromSyncMessage(syncIssues)?.let {
          SuppressUnsupportedSdkVersionHyperlink(it)
        }
      }
    }
    return null
  }

  private fun tryExtractPropertyFromSyncMessage(syncIssues: MutableList<IdeSyncIssue>): String? {
    val message = syncIssues[0].message
    val matchResult = PATTERN.find(message)
    return matchResult?.value
  }

  companion object {
    val PATTERN = Regex("android.suppressUnsupportedCompileSdk=(\\S+)")
  }
}