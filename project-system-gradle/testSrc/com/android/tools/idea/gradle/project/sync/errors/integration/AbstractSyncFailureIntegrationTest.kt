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
package com.android.tools.idea.gradle.project.sync.errors.integration

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.util.containers.ContainerUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class AbstractSyncFailureIntegrationTest {

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()


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

  protected fun runSyncAndCheckGeneralFailure(
    preparedProject: PreparedTestProject,
    verifySyncViewEvents: (List<BuildEvent>) -> Unit,
    verifyFailureReported: (AndroidStudioEvent) -> Unit
  ) {
    val buildEvents = ContainerUtil.createConcurrentList<BuildEvent>()
    val allBuildEventsProcessedLatch = CountDownLatch(1)
    preparedProject.open(
      updateOptions = {
        it.copy(
          verifyOpened = { project ->
            // Do not use 'expect' here. If this fails further checks make no sense.
            Truth.assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult())
              .isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
          },
          syncExceptionHandler = {},
          syncViewEventHandler = { buildEvent ->
            buildEvents.add(buildEvent)
            // Events are generated in a separate thread(s) and if we don't wait for the FinishBuildEvent
            // some might not reach here by the time we inspect them below resulting in flakiness (like b/318490086).
            if (buildEvent is FinishBuildEventImpl) {
              allBuildEventsProcessedLatch.countDown()
            }
          }
        )
      }
    ) {
      allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)
    }

    verifySyncViewEvents(buildEvents)

    val reportedFailureDetails = usageTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }
    expect.that(reportedFailureDetails).hasSize(1)
    reportedFailureDetails.map { it.studioEvent }.firstOrNull()?.let { verifyFailureReported(it) }
  }

  fun List<BuildEvent>.finishEventFailures() = (filterIsInstance<FinishBuildEvent>().single().result as FailureResult).failures

  fun GradleSyncStats.printPhases() = gradleSyncPhasesDataList.joinToString(separator = "\n") {
    it.phaseResult.name + " : " + it.phaseStackList.joinToString(separator = "/") { it.name }
  }
}