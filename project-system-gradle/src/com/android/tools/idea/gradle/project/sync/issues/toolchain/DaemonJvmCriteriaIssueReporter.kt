/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.toolchain

import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.jdk.JdkAnalyticsTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.GRADLE_DAEMON_JVM_CRITERIA_ERROR
import com.google.wireless.android.sdk.stats.GradleDaemonJvmCriteriaErrorEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData

/**
 * Represents a wrapper over existing platform Daemon JVM Criteria issue checkers, to handle related errors and report metrics to analytics
 * build issues linked to [Daemon Jvm Criteria](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria).
 *
 * Any implementation must be registered with 'order="first"' in order to be prioritized over the original platform checker
 * and avoid returning any value for [GradleIssueChecker.check] in order to not interfere with the original ordering.
 */
abstract class DaemonJvmCriteriaIssueReporter(
  private val checker: GradleIssueChecker,
  private val errorEvent: GradleDaemonJvmCriteriaErrorEvent.Error
) : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    checker.check(issueData)?.run {
      JdkAnalyticsTracker.reportDaemonJvmCriteriaException(issueData.projectPath, errorEvent)
      SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, GRADLE_DAEMON_JVM_CRITERIA_ERROR)
    }
    return null
  }
}