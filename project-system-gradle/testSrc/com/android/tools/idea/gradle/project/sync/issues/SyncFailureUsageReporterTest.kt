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
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.ANDROID_BUILD_ISSUE_CREATED_UNKNOWN_FAILURE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.BUILD_ISSUE_CREATED_UNKNOWN_FAILURE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CLASS_NOT_FOUND
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNKNOWN_GRADLE_FAILURE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION
import com.intellij.build.BuildProgressListener
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.internal.DummySyncViewManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.testFramework.replaceService
import com.intellij.util.containers.DisposableWrapperList
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.DeprecatedGradleVersionIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.issue.UnsupportedGradleVersionIssueChecker
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SyncFailureUsageReporterTest {
  
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())
  private val syncViewListeners = DisposableWrapperList<BuildProgressListener>()
  private lateinit var buildId: ExternalSystemTaskId


  @Before
  fun setUp() {
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
    projectRule.project.replaceService(
      SyncViewManager::class.java,
      object : DummySyncViewManager(projectRule.project) {
        override fun addListener(listener: BuildProgressListener, disposable: Disposable) {
          if (listener::class.java.enclosingClass == SyncFailureUsageReporter::class.java) {
            syncViewListeners.add(listener, disposable)
          }
        }
      },
      projectRule.testRootDisposable
    )
    UsageTracker.setWriterForTest(usageTracker)
  }

  @After
  fun cleanUp() {
    usageTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun detectedAndReportedFailureReasonReportedAsIs() {
    SyncFailureUsageReporter.getInstance().onSyncStart(buildId, projectRule.project, projectRule.project.basePath!!)

    SyncFailureUsageReporter.getInstance().collectFailure(projectRule.project.basePath!!, CLASS_NOT_FOUND)

    val exception = BuildIssueException(BuildIssueComposer("Test error").composeBuildIssue())
    SyncFailureUsageReporter.getInstance().collectProcessedError(buildId, projectRule.project, projectRule.project.basePath!!, exception)

    sendBuildFinishedEvent(exception)

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(CLASS_NOT_FOUND)
  }

  @Test
  fun detectedButUnreportedBuildIssueOriginatedInAndroid() {
    SyncFailureUsageReporter.getInstance().onSyncStart(buildId, projectRule.project, projectRule.project.basePath!!)

    val exception = BuildIssueException(BuildIssueComposer("Test error").composeBuildIssue())
    SyncFailureUsageReporter.getInstance().collectProcessedError(buildId, projectRule.project, projectRule.project.basePath!!, exception)

    sendBuildFinishedEvent(exception)

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(ANDROID_BUILD_ISSUE_CREATED_UNKNOWN_FAILURE)
  }

  /**
   * The build issue under test here is generated in platform in [UnsupportedGradleVersionIssueChecker]. We detect this issue
   * by matching the name as the class became private.
   * This test is needed to make sure this does not change silently without us knowing, it uses the same code path of generating
   * this issue as in production code.
   */
  @Test
  fun detectedInPlatformCodeUnsupportedGradleBuildIssue() {
    SyncFailureUsageReporter.getInstance().onSyncStart(buildId, projectRule.project, projectRule.project.basePath!!)

    // Emulate process of issue being handled by platform's UnsupportedGradleVersionIssueChecker
    val issueData = GradleIssueData(
      projectRule.project.basePath!!,
      GradleExecutionHelper.UnsupportedGradleVersionByIdeaException(GradleVersion.version("2.6")),
      null, null
    )
    val issue = UnsupportedGradleVersionIssueChecker().check(issueData)!!
    val exception = BuildIssueException(issue)

    SyncFailureUsageReporter.getInstance().collectProcessedError(buildId, projectRule.project, projectRule.project.basePath!!, exception)

    sendBuildFinishedEvent(exception)

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(UNSUPPORTED_GRADLE_VERSION)
  }

  @Test
  fun detectedInPlatformCodeBuildIssue() {
    SyncFailureUsageReporter.getInstance().onSyncStart(buildId, projectRule.project, projectRule.project.basePath!!)

    val exception = BuildIssueException(
      DeprecatedGradleVersionIssue(GradleVersion.version("1.0.0"), projectRule.project.basePath!!)
    )
    SyncFailureUsageReporter.getInstance().collectProcessedError(buildId, projectRule.project, projectRule.project.basePath!!, exception)

    sendBuildFinishedEvent(exception)

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(BUILD_ISSUE_CREATED_UNKNOWN_FAILURE)
  }

  @Test
  fun unprocessedFailureReason() {
    SyncFailureUsageReporter.getInstance().onSyncStart(buildId, projectRule.project, projectRule.project.basePath!!)

    val exception = ExternalSystemException("Test Failure")
    SyncFailureUsageReporter.getInstance().collectProcessedError(buildId, projectRule.project, projectRule.project.basePath!!, exception)

    sendBuildFinishedEvent(exception)

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(UNKNOWN_GRADLE_FAILURE)
  }

  @Test
  fun testReporterOnSyncSuccess() {
    SyncFailureUsageReporter.getInstance().onSyncStart(buildId, projectRule.project, projectRule.project.basePath!!)

    sendEventToListeners(
      buildId,
      FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "finished", SuccessResultImpl())
    )

    // check that listener was removed up after event
    Truth.assertThat(syncViewListeners).hasSize(0)
    // check no stats reported
    Truth.assertThat(usageTracker.usages.filter{ it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }).isEmpty()
  }

  @Test
  fun testListenerNotRemovedForDifferentBuildFinished() {
    val otherBuildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)

    SyncFailureUsageReporter.getInstance().onSyncStart(buildId, projectRule.project, projectRule.project.basePath!!)
    val firstListener = syncViewListeners[0]

    SyncFailureUsageReporter.getInstance().onSyncStart(otherBuildId, projectRule.project, projectRule.project.basePath!!)
    Truth.assertThat(syncViewListeners).hasSize(2)

    sendEventToListeners(buildId, FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "failed", FailureResultImpl()))
    Truth.assertThat(syncViewListeners).hasSize(1)
    Truth.assertThat(syncViewListeners).doesNotContain(firstListener)
  }

  private fun sendBuildFinishedEvent(exception: Throwable) {
    // check that listener was added and still waiting for event
    Truth.assertThat(syncViewListeners).hasSize(1)

    sendEventToListeners(
      buildId,
      FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "failed", ExternalSystemUtil.createFailureResult(
        "Gradle project reload failed",
        exception,
        GradleConstants.SYSTEM_ID,
        projectRule.project,
        projectRule.project.basePath!!,
        DataContext.EMPTY_CONTEXT
      ))
    )

    // check that listener was removed up after event
    Truth.assertThat(syncViewListeners).hasSize(0)
  }

  private fun sendEventToListeners(buildId: Any, event: BuildEvent) {
    syncViewListeners.forEach { it.onEvent(buildId, event) }
  }
}
