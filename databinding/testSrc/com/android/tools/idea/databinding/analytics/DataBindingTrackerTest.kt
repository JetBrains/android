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

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.facet.FacetManager
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DataBindingTrackerTest(private val mode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.NONE,
                         DataBindingMode.ANDROIDX)
  }

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk().initAndroid(true)

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.fixture.copyDirectoryToProject(TestDataPaths.PROJECT_FOR_TRACKING, "src")
    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    ModuleDataBinding.getInstance(androidFacet!!).setMode(mode)
  }

  @Test
  fun testDataBindingEnabledTracking() {
    val tracker = TestUsageTracker(VirtualTimeScheduler())
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      try {
        UsageTracker.setWriterForTest(tracker)
        val syncState = GradleSyncState.getInstance(projectRule.project)
        syncState.syncStarted(true, GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED))
        syncState.syncEnded()

        assertThat(
          tracker.usages
            .map { it.studioEvent }
            .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
            .map { it.dataBindingEvent.pollMetadata.dataBindingEnabled }
            .last())
          .isEqualTo(mode != DataBindingMode.NONE)
      }
      finally {
        tracker.close()
        UsageTracker.cleanAfterTesting()
      }
    }
  }

  @Test
  fun testDataBindingPollingMetadataTracking() {
    val tracker = TestUsageTracker(VirtualTimeScheduler())
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      try {
        UsageTracker.setWriterForTest(tracker)
        val buildState = GradleBuildState.getInstance(projectRule.project)
        buildState.buildFinished(BuildStatus.SUCCESS)
        val dataBindingPollMetadata = tracker.usages
          .map { it.studioEvent }
          .filter { it.kind == AndroidStudioEvent.EventKind.DATA_BINDING }
          .map { it.dataBindingEvent.pollMetadata }
          .lastOrNull()

        if (mode == DataBindingMode.NONE) {
          assertThat(dataBindingPollMetadata).isNull()
        }
        else {
          dataBindingPollMetadata!!
          assertThat(dataBindingPollMetadata.layoutXmlCount).isEqualTo(3)
          assertThat(dataBindingPollMetadata.importCount).isEqualTo(0)
          assertThat(dataBindingPollMetadata.variableCount).isEqualTo(7)
          assertThat(dataBindingPollMetadata.expressionCount).isEqualTo(5)
        }
      }
      finally {
        tracker.close()
        UsageTracker.cleanAfterTesting()
      }
    }
  }
}