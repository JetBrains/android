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
package com.android.tools.idea.databinding.analytics

import com.android.ide.common.blame.Message
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.databinding.TestDataPaths.PROJECT_FOR_TRACKING
import com.android.tools.idea.databinding.TestDataPaths.TEST_DATA_ROOT
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@Ignore("b/129763461")
class ObservableAndLiveDataTrackingHelperTest {
  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TEST_DATA_ROOT
    projectRule.load(PROJECT_FOR_TRACKING)
  }

  @Test
  @RunsInEdt
  fun testTrackObservablesAndLiveData() {
    val assembleDebug = projectRule.invokeTasks("assembleDebug")
    assertWithMessage(assembleDebug.getCompilerMessages(Message.Kind.ERROR).joinToString("\n"))
      .that(assembleDebug.isBuildSuccessful).isTrue()
    val syncState = GradleSyncState.getInstance(projectRule.project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()

    val tracker = TestUsageTracker(VirtualTimeScheduler())
    try {
      UsageTracker.setWriterForTest(tracker)
      GradleBuildState.getInstance(projectRule.project).buildFinished(BuildStatus.SUCCESS)
      val pollMetadata = tracker.usages
        .map { it.studioEvent }
        .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
        .map { it.dataBindingEvent.pollMetadata }
        .lastOrNull()!!

      assertThat(pollMetadata.observableMetrics.primitiveCount).isEqualTo(3)
      assertThat(pollMetadata.observableMetrics.collectionCount).isEqualTo(1)
      assertThat(pollMetadata.observableMetrics.observableObjectCount).isEqualTo(5)
      assertThat(pollMetadata.liveDataMetrics.liveDataObjectCount).isEqualTo(1)
    }
    finally {
      tracker.close()
      UsageTracker.cleanAfterTesting()
    }
  }
}