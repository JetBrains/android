/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.buildAndWait
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.utils.filterIsInstanceAnd
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * This currently does not fail sync as Configuration Cache is not active during sync.
 * THus this has to be tested on build, but since infrastructure for parsing errors is shared between build and sync
 * it is still belong here.
 */
class ConfigurationCacheFailureTest {

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

  @Test
  fun testConfigurationCacheFailure() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    preparedProject.root.resolve(SdkConstants.FN_GRADLE_PROPERTIES).let {
      it.appendText("\norg.gradle.configuration-cache=true")
    }
    preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE).let {
      it.appendText("\ngradle.addBuildListener(new BuildAdapter())")
    }

    val buildEvents = ContainerUtil.createConcurrentList<BuildEvent>()
    val allBuildEventsProcessedLatch = CountDownLatch(1)

    preparedProject.open {
      val result = it.buildAndWait(
        eventHandler = { buildEvent ->
          buildEvents.add(buildEvent)
          // Events are generated in a separate thread(s) and if we don't wait for the FinishBuildEvent
          // some might not reach here by the time we inspect them below resulting in flakiness (like b/318490086).
          if (buildEvent is FinishBuildEventImpl) {
            allBuildEventsProcessedLatch.countDown()
          }
        }
      ) { invoker ->
        invoker.cleanProject()
      }
      Truth.assertThat(result.isBuildSuccessful).isFalse()
      allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)

      // Expect single BuildIssueEvent on Sync Output
      buildEvents.filterIsInstance<BuildIssueEvent>().let { events ->
        expect.that(events).hasSize(1)
        events.firstOrNull()?.let { expect.that(it.message).isEqualTo("Configuration cache problems found in this build.") }
      }
      // Make sure no additional error build issue events are generated
      expect.that(buildEvents.filterIsInstanceAnd<MessageEvent> { it !is BuildIssueEvent }).isEmpty()
      expect.that(buildEvents.finishEventFailures()).isEmpty()

      val reportedFailureDetails = usageTracker.usages
        .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS }
      expect.that(reportedFailureDetails).hasSize(1)
      reportedFailureDetails.map { it.studioEvent }.firstOrNull()?.let {
        expect.that(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
          .containsExactly(BuildErrorMessage.ErrorType.CONFIGURATION_CACHE)
      }

    }
  }

  private fun List<BuildEvent>.finishEventFailures() = (filterIsInstance<FinishBuildEvent>().single().result as FailureResult).failures
}