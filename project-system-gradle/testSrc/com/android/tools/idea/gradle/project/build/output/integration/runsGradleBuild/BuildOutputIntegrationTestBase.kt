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
package com.android.tools.idea.gradle.project.build.output.integration.runsGradleBuild

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.buildAndWait
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail

abstract class BuildOutputIntegrationTestBase {

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

  fun Project.buildCollectingEvents(expectSuccess: Boolean): List<BuildEvent> {
    val buildEvents = ContainerUtil.createConcurrentList<BuildEvent>()
    val allBuildEventsProcessedLatch = CountDownLatch(1)
    // Build
    val result = buildAndWait(eventHandler = { event ->
      if (event !is BuildIssueEvent && event !is MessageEvent && event !is FinishBuildEvent) return@buildAndWait
      buildEvents.add(event)
      // Events are generated in a separate thread(s) and if we don't wait for the FinishBuildEvent
      // some might not reach here by the time we inspect them below resulting in flakiness (like b/318490086).
      if (event is FinishBuildEventImpl) {
        allBuildEventsProcessedLatch.countDown()
      }
    }) { buildInvoker ->
      buildInvoker.rebuild()
    }
    assertThat(result.isBuildSuccessful).isEqualTo(expectSuccess)
    allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)
    return buildEvents
  }

  fun List<BuildEvent>.finishEventFailures() = (filterIsInstance<FinishBuildEvent>().single().result as? FailureResult)?.failures
                                                       ?: emptyList()

  fun verifyNoStats() {
    val reportedFailureDetails = usageTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS }
    assertThat(reportedFailureDetails).hasSize(0)
  }
  fun verifyStats(vararg expectedMessages: BuildErrorMessage.ErrorType) {
    usageTracker.usages.map { it.studioEvent }.firstOrNull {
      it.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS
    }?.also {
      assertThat(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
        .containsExactly(*expectedMessages)
    } ?: fail("No BUILD_OUTPUT_WINDOW_STATS event reported.")
  }

  fun BuildEvent.verifyQuickfix(quickFixId: String, verify: (BuildIssueQuickFix) -> Unit) {
    findQuickfix(quickFixId)?.let {verify(it) } ?: Assert.fail("Quickfix with id '$quickFixId' not found")
  }
  fun List<BuildEvent>.printEvents(): String {
    return joinToString(separator = "\n") { it.toFullPathWithMessage() }
  }
  fun BuildEvent.toFullPathWithMessage(): String {
    val parentPath = when (val parentId = parentId) {
      null, is ExternalSystemTaskId -> "root"
      else -> "root > ${parentId.toString().substringAfter(" > ")}"
    }
    val kind = if (this is MessageEvent) "$kind:" else ""
    return "$parentPath > $kind'${message}'"
  }

  fun List<BuildEvent>.findBuildEvent(eventPath: String): BuildEvent {
    return single { it.toFullPathWithMessage() == eventPath }
  }

  fun BuildEvent.findQuickfix(quickfixId: String): BuildIssueQuickFix? {
    return (this as? BuildIssueEvent)?.issue?.quickFixes?.firstOrNull { it.id == quickfixId }
  }
}