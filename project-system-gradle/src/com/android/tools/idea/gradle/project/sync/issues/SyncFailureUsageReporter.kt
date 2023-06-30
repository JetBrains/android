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
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.concurrentMapOf
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
  private val collectedFailures = concurrentMapOf<String, AndroidStudioEvent.GradleSyncFailure>()

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

  fun reportFailure(syncStateHolder: GradleSyncStateHolder, rootProjectPath: @SystemIndependent String) {
    collectedFailures.remove(rootProjectPath).let {
      UsageTracker.log(
        syncStateHolder.generateSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS, rootProjectPath)
          .setGradleSyncFailure(it ?: AndroidStudioEvent.GradleSyncFailure.UNKNOWN_GRADLE_FAILURE)
      )
    }
  }
}

class FailuresReporterSyncListener : GradleSyncListenerWithRoot {
  override fun syncStarted(project: Project, rootProjectPath: String) {
    SyncFailureUsageReporter.getInstance().onSyncStart(rootProjectPath)
  }

  override fun syncFailed(project: Project, errorMessage: String, rootProjectPath: String) {
    SyncFailureUsageReporter.getInstance().reportFailure(GradleSyncStateHolder.getInstance(project), rootProjectPath)
  }
}