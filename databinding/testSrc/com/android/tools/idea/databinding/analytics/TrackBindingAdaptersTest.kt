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
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DataBindingEvent
import com.intellij.facet.FacetManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class TrackBindingAdaptersTest {
  private val projectRule = AndroidProjectRule.withSdk().initAndroid(true)

  @get:Rule
  val rule = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture: JavaCodeInsightTestFixture by lazy {
    projectRule.fixture as JavaCodeInsightTestFixture
  }

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.fixture.copyDirectoryToProject(TestDataPaths.PROJECT_FOR_TRACKING, "src")
    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    ModuleDataBinding.getInstance(androidFacet!!).setMode(DataBindingMode.ANDROIDX)
  }

  @Test
  fun testTrackBindingAdapters() {
    val tracker = TestUsageTracker(VirtualTimeScheduler())
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      try {
        UsageTracker.setWriterForTest(tracker)

        val buildState = GradleBuildState.getInstance(projectRule.project)
        buildState.buildFinished(BuildStatus.SUCCESS)
        val adapterMetrics = tracker.usages
          .map { it.studioEvent }
          .filter {
            it.kind == AndroidStudioEvent.EventKind.DATA_BINDING
            && it.dataBindingEvent.type == DataBindingEvent.EventType.DATA_BINDING_BUILD_EVENT
          }
          .map { it.dataBindingEvent.pollMetadata.bindingAdapterMetrics }
          .last()

        adapterMetrics!!
        assertThat(adapterMetrics.adapterCount).isEqualTo(9)
      }
      finally {
        tracker.close()
        UsageTracker.cleanAfterTesting()
      }
    }
  }
}
