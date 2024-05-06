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

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.ANDROID_BUILD_ISSUE_CREATED_UNKNOWN_FAILURE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.BUILD_ISSUE_CREATED_UNKNOWN_FAILURE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CLASS_NOT_FOUND
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNKNOWN_GRADLE_FAILURE
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.DeprecatedGradleVersionIssue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SyncFailureUsageReporterTest {
  
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(usageTracker)
  }

  @After
  fun cleanUp() {
    usageTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun detectedAndReportedFailureReasonReportedAsIs() {

    SyncFailureUsageReporter.getInstance().collectFailure(projectRule.project.basePath!!, CLASS_NOT_FOUND)

    val exception = BuildIssueException(BuildIssueComposer("Test error").composeBuildIssue())
    SyncFailureUsageReporter.getInstance().reportFailure(
      GradleSyncStateHolder.getInstance(projectRule.project),
      projectRule.project.basePath!!,
      exception
    )

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(CLASS_NOT_FOUND)
  }

  @Test
  fun detectedButUnreportedBuildIssueOriginatedInAndroid() {
    val exception = BuildIssueException(BuildIssueComposer("Test error").composeBuildIssue())
    SyncFailureUsageReporter.getInstance().reportFailure(
      GradleSyncStateHolder.getInstance(projectRule.project),
      projectRule.project.basePath!!,
      exception
    )

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(ANDROID_BUILD_ISSUE_CREATED_UNKNOWN_FAILURE)
  }

  @Test
  fun detectedInPlatformCodeBuildIssue() {
    val exception = BuildIssueException(
      DeprecatedGradleVersionIssue(GradleVersion.version("1.0.0"), projectRule.project.basePath!!)
    )
    SyncFailureUsageReporter.getInstance().reportFailure(
      GradleSyncStateHolder.getInstance(projectRule.project),
      projectRule.project.basePath!!,
      exception
    )

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(BUILD_ISSUE_CREATED_UNKNOWN_FAILURE)
  }

  @Test
  fun unprocessedFailureReason() {
    val exception = ExternalSystemException("Test Failure")
    SyncFailureUsageReporter.getInstance().reportFailure(
      GradleSyncStateHolder.getInstance(projectRule.project),
      projectRule.project.basePath!!,
      exception
    )

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(UNKNOWN_GRADLE_FAILURE)
  }
}