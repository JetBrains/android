/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.tools.idea.flags.StudioFlags
// TODO: android-merge; com.android.tools.sherlock is Studio's Sherlock profiler, which this repository does not carry.
// import com.android.tools.sherlock.common.system.utils.FeatureFlags
// import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@Ignore("TODO: android-merge; both cases assert on Sherlock's FeatureFlags, which this repository does not carry.")
class SherlockFlagsTest {

  // Required for Sherlock's FeatureFlags to be able to access IdeInfo service.
  @get:Rule val projectRule = ProjectRule()

  @After
  fun tearDown() {
    StudioFlags.PROFILER_PERFETTO_QUERY_GENERATION.clearOverride()
    StudioFlags.PROFILER_PERFETTO_AI_TRACE_ANALYSIS.clearOverride()
  }

  @Test
  fun testQueryGenerationFlag() {
    // TODO: android-merge; FeatureFlags is Sherlock's, which this repository does not carry.
    // // Verify the default value.
    // assertThat(FeatureFlags.queryGenerationEnabled).isFalse()
    //
    // // Verify we can override the value, and can read properly.
    // StudioFlags.PROFILER_PERFETTO_QUERY_GENERATION.override(true)
    // assertThat(FeatureFlags.queryGenerationEnabled).isTrue()
    //
    // StudioFlags.PROFILER_PERFETTO_QUERY_GENERATION.override(false)
    // assertThat(FeatureFlags.queryGenerationEnabled).isFalse()
  }

  @Test
  fun testAiTraceAnalysisFlag() {
    // TODO: android-merge; FeatureFlags is Sherlock's, which this repository does not carry.
    // // Verify the default value.
    // assertThat(FeatureFlags.aiTraceAnalysisEnabled).isFalse()
    //
    // // Verify we can override the value, and can read properly.
    // StudioFlags.PROFILER_PERFETTO_AI_TRACE_ANALYSIS.override(true)
    // assertThat(FeatureFlags.aiTraceAnalysisEnabled).isTrue()
    //
    // StudioFlags.PROFILER_PERFETTO_AI_TRACE_ANALYSIS.override(false)
    // assertThat(FeatureFlags.aiTraceAnalysisEnabled).isFalse()
  }
}
