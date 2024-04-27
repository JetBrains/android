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

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import org.jetbrains.annotations.SystemIndependent

private val LOG = Logger.getInstance(SyncFailureUsageReporter::class.java)

/**
 * This service is responsible for collecting sync failure information to be reported to metrics.
 * Failure means that an exception was thrown from Gradle during sync and if this exception is recognised during exception handling
 * process the corresponding value will be collected in this service. In the end, in `onSyncFailure` listener call, collected value is
 * reported to the metrics.
 */
@Service(Service.Level.APP)
class SyncFailureUsageReporter {
  private val collectedFailures = ConcurrentCollectionFactory.createConcurrentMap<String, AndroidStudioEvent.GradleSyncFailure>()

  companion object {
    @JvmStatic
    fun getInstance(): SyncFailureUsageReporter = ApplicationManager.getApplication().getService(SyncFailureUsageReporter::class.java)
  }

  fun onSyncStart(rootProjectPath: @SystemIndependent String) {
    collectedFailures.remove(rootProjectPath)
  }

  fun collectFailure(rootProjectPath: @SystemIndependent String, failure: AndroidStudioEvent.GradleSyncFailure) {
    val previousValue = collectedFailures.put(rootProjectPath, failure)
    if (previousValue != null) {
      LOG.warn("Multiple sync failures reported. Discarding: $previousValue")
    }
  }

  fun reportFailure(syncStateHolder: GradleSyncStateHolder, rootProjectPath: @SystemIndependent String, processedError: Throwable?) {
    // If nothing was collected by the issue checkers try to derive a bit more details from the processed error.
    // e.g. if it has a BuildIssue attached then something just did not report the recognized failure.
    val failureType = collectedFailures.remove(rootProjectPath) ?: deriveSyncFailureFromProcessedError(processedError)
    UsageTracker.log(
      syncStateHolder
        .generateSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS, rootProjectPath)
        .setGradleSyncFailure(failureType)
    )
  }

  private fun deriveSyncFailureFromProcessedError(error: Throwable?) = if (error is BuildIssueException) {
    if (error.buildIssue.javaClass.packageName.startsWith("com.android.tools.")) {
      AndroidStudioEvent.GradleSyncFailure.ANDROID_BUILD_ISSUE_CREATED_UNKNOWN_FAILURE
    }
    else {
      AndroidStudioEvent.GradleSyncFailure.BUILD_ISSUE_CREATED_UNKNOWN_FAILURE
    }
  }
  else {
    AndroidStudioEvent.GradleSyncFailure.UNKNOWN_GRADLE_FAILURE
  }
}
