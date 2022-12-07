/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.tracker.gradle

import com.android.flags.junit.FlagRule
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.TestDataPaths
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.NavSafeArgsEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Verify that we can sync a Gradle project that applies the safe args plugin.
 */
@RunsInEdt
@RunWith(Parameterized::class)
class SafeArgsTrackerTest(private val params: TestParams) {
  data class TestParams(val project: String, val flagEnabled: Boolean, val verify: (NavSafeArgsEvent?) -> Unit) {
    override fun toString(): String {
      return "$project (flag enabled = $flagEnabled)"
    }
  }

  companion object {
    @Suppress("unused") // Accessed via reflection by JUnit
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val parameters = listOf(
      TestParams(TestDataPaths.MULTI_MODULE_PROJECT, true) { event ->
        event!!
        assertThat(event.eventContext).isEqualTo(NavSafeArgsEvent.EventContext.SYNC_EVENT_CONTEXT)
        event.projectMetadata.let { projectMetadata ->
          assertThat(projectMetadata.moduleCount).isEqualTo(3)
          assertThat(projectMetadata.javaPluginCount).isEqualTo(1)
          assertThat(projectMetadata.kotlinPluginCount).isEqualTo(1)
        }
      },
      TestParams(TestDataPaths.PROJECT_USING_JAVA_PLUGIN, true) { event ->
        event!!
        assertThat(event.eventContext).isEqualTo(NavSafeArgsEvent.EventContext.SYNC_EVENT_CONTEXT)
        event.projectMetadata.let { projectMetadata ->
          assertThat(projectMetadata.moduleCount).isEqualTo(1)
          assertThat(projectMetadata.javaPluginCount).isEqualTo(1)
          assertThat(projectMetadata.kotlinPluginCount).isEqualTo(0)
        }
      },
      TestParams(TestDataPaths.PROJECT_USING_KOTLIN_PLUGIN, true) { event ->
        event!!
        assertThat(event.eventContext).isEqualTo(NavSafeArgsEvent.EventContext.SYNC_EVENT_CONTEXT)
        event.projectMetadata.let { projectMetadata ->
          assertThat(projectMetadata.moduleCount).isEqualTo(1)
          assertThat(projectMetadata.javaPluginCount).isEqualTo(0)
          assertThat(projectMetadata.kotlinPluginCount).isEqualTo(1)
        }
      },
      TestParams(TestDataPaths.MULTI_MODULE_PROJECT, false) { event -> assertThat(event).isNull() },
      TestParams(TestDataPaths.PROJECT_USING_JAVA_PLUGIN, false) { event -> assertThat(event).isNull() },
      TestParams(TestDataPaths.PROJECT_USING_KOTLIN_PLUGIN, false) { event -> assertThat(event).isNull() },
      // Projects not using safe args don't generate safe args metrics
      TestParams(TestDataPaths.PROJECT_WITHOUT_SAFE_ARGS, true) { event -> assertThat(event).isNull() })
  }

  private val projectRule = AndroidGradleProjectRule()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @get:Rule
  val restoreSafeArgsFlagRule = FlagRule(StudioFlags.NAV_SAFE_ARGS_SUPPORT)

  private val fixture get() = projectRule.fixture as JavaCodeInsightTestFixture

  @Test
  fun verifyExpectedAnalytics() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(params.flagEnabled)
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    val tracker = TestUsageTracker(VirtualTimeScheduler())

    try {
      UsageTracker.setWriterForTest(tracker)
      projectRule.load(params.project)
      projectRule.requestSyncAndWait()

      val safeArgsEvent = tracker.usages
        .map { it.studioEvent }
        .filter { it.kind == AndroidStudioEvent.EventKind.NAV_SAFE_ARGS_EVENT }
        .map { it.navSafeArgsEvent }
        .lastOrNull()

      params.verify(safeArgsEvent)
    }
    finally {
      tracker.close()
      UsageTracker.cleanAfterTesting()
    }
  }
}