/*
 * Copyright (C) 2022 The Android Open Source Project
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
@file:JvmName("SyncIssueUsageReporterUtils")

package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.project.messages.SyncMessage
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.VisibleForTesting

private val LOG = Logger.getInstance(SyncIssueUsageReporter::class.java)

/**
 * This service is responsible for collecting and then reporting sync issues not necessarily leading to the failure.
 * Failure type [AndroidStudioEvent.GradleSyncFailure] is now collected and reported in [SyncFailureUsageReporter].
 */
interface SyncIssueUsageReporter {

  /**
   * Collects a reported sync issue details to be reported as a part of [AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES] event. This
   * method is supposed to be called on EDT only.
   */
  fun collect(issue: GradleSyncIssue)

  /**
   * Logs collected usages to the usage tracker as a [AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES] event.
   * This method is supposed to be called on EDT only.
   */
  fun reportToUsageTracker(rootProjectPath: @SystemIndependent String)

  companion object {
    fun getInstance(project: Project): SyncIssueUsageReporter {
      return project.getService(SyncIssueUsageReporter::class.java)
    }

    @JvmStatic
    fun createGradleSyncIssue(issueType: Int, message: SyncMessage): GradleSyncIssue {
      return GradleSyncIssue
        .newBuilder()
        .setType(issueType.toGradleSyncIssueType() ?: AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE)
        .addAllOfferedQuickFixes(
          message.quickFixes.flatMap { it.quickFixIds }.distinct()
        )
        .build()
    }

    @JvmStatic
    fun createGradleSyncIssues(issueType: Int, messages: List<SyncMessage>): List<GradleSyncIssue> {
      return messages.map { createGradleSyncIssue(issueType, it) }
    }

    private val intToSyncIssueMap = AndroidStudioEvent.GradleSyncIssueType.values()
      .mapNotNull { it.name.let {
        name -> try { IdeSyncIssue::class.java.getDeclaredField(name).get(null) } catch(e: NoSuchFieldException) { null }
          ?.let { value -> (value as? Int)?.let { int -> int to it } } } }
      .toMap()

    @VisibleForTesting
    fun Int.toGradleSyncIssueType(): AndroidStudioEvent.GradleSyncIssueType? =
      intToSyncIssueMap[this] ?: null.also { LOG.warn("Unknown sync issue type: $this") }
  }
}


fun SyncIssueUsageReporter.collect(issueType: Int, messages: List<SyncMessage>) {
  SyncIssueUsageReporter.createGradleSyncIssues(issueType, messages).forEach { collect(it) }
}
