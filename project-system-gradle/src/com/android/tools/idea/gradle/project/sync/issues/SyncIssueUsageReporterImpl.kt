/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent

class SyncIssueUsageReporterImpl(private val project: Project) : SyncIssueUsageReporter {
  private val collectedIssues = mutableListOf<GradleSyncIssue>()

  override fun reportToUsageTracker(rootProjectPath: @SystemIndependent String) {
    if (collectedIssues.isNotEmpty()) {
      UsageTracker.log(
        GradleSyncStateHolder
          .getInstance(project)
          .generateSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES, rootProjectPath)
          .addAllGradleSyncIssues(collectedIssues))
      collectedIssues.clear()
    }
  }

  override fun collect(issue: GradleSyncIssue) {
    collectedIssues.add(issue)
  }
}

