/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.ide.common.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.errors.tryExtractPreferredNdkDownloadVersion
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallNdkHyperlink
import com.android.tools.idea.gradle.project.sync.issues.CxxConfigurationIssuesReporter.Classification.MISSING_NDK_WITH_PREFERRED_VERSION
import com.android.tools.idea.gradle.project.sync.issues.CxxConfigurationIssuesReporter.Classification.NOT_ACTIONABLE
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * IssuesReporter for C/C++ configuration.
 */
class CxxConfigurationIssuesReporter : SimpleDeduplicatingSyncIssueReporter() {

  override fun getDeduplicationKey(issue: IdeSyncIssue) : Any = classifySyncIssue(issue)

  override fun getSupportedIssueType() = IdeSyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION

  override fun getCustomLinks(project: Project,
                              syncIssues: List<IdeSyncIssue>,
                              affectedModules: List<Module>,
                              buildFileMap: MutableMap<Module, VirtualFile>): List<NotificationHyperlink> {
    return when(classifySyncIssue(syncIssues[0])) {
      MISSING_NDK_WITH_PREFERRED_VERSION ->
        // Recognize missing NDK sync issue and offer to install the preferred version.
        // If there are multiple, then choose the NDK with the highest version number.
        syncIssues.asSequence().mapNotNull { syncIssue ->
          tryExtractPreferredNdkDownloadVersion(syncIssue.message)
        }
        .sortedByDescending { revision -> revision }
        .take(1)
        .map { preferredVersion ->
          InstallNdkHyperlink(preferredVersion.toString(), buildFileMap.values.toList())
        }.toList()
      NOT_ACTIONABLE -> listOf()
    }
  }

  private enum class Classification {
    MISSING_NDK_WITH_PREFERRED_VERSION,
    NOT_ACTIONABLE
  }

  /**
   * Determine which action, if any, is available to address this issue.
   */
  private fun classifySyncIssue(issue: IdeSyncIssue) =
    when {
      tryExtractPreferredNdkDownloadVersion(issue.message) != null -> MISSING_NDK_WITH_PREFERRED_VERSION
      else -> NOT_ACTIONABLE
    }
}