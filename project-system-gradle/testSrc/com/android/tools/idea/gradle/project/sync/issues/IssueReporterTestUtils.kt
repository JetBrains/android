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

import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import junit.framework.Assert.assertSame
import org.jetbrains.annotations.SystemIndependent

class TestSyncIssueUsageReporter(
    val collectedIssue: MutableList<GradleSyncIssue> = mutableListOf(),
    var collectedFailure: AndroidStudioEvent.GradleSyncFailure? = null,
    val collectedQuickFixes: MutableList<AndroidStudioEvent.GradleSyncQuickFix> = mutableListOf()
) : SyncIssueUsageReporter {
  override fun collect(failure: AndroidStudioEvent.GradleSyncFailure) {
    collectedFailure = failure
  }

  override fun collect(quickFixes: Collection<AndroidStudioEvent.GradleSyncQuickFix>) {
    collectedQuickFixes.addAll(quickFixes)
  }

  override fun collect(issue: GradleSyncIssue.Builder) {
    collectedIssue.add(issue.build())
  }

  override fun reportToUsageTracker(rootProjectPath: @SystemIndependent String) = Unit

  companion object {
    @JvmStatic
    fun replaceSyncMessagesService(project: Project, parentDisposable: Disposable): TestSyncIssueUsageReporter {
      val syncMessages = TestSyncIssueUsageReporter()
      project.replaceService(SyncIssueUsageReporter::class.java, syncMessages, parentDisposable)
      assertSame(syncMessages, SyncIssueUsageReporter.getInstance(project))
      return syncMessages
    }
  }
}
