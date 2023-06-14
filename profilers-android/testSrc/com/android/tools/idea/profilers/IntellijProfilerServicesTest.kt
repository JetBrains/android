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
package com.android.tools.idea.profilers
import com.android.tools.idea.flags.StudioFlags
import org.junit.AfterClass
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntellijProfilerServicesTest {
  companion object {
    @JvmStatic
    @AfterClass
    fun tearDown() {
      StudioFlags.PROFILER_TRACEBOX.clearOverride()
    }
  }

  @Test
  fun featureFlagConfigTraceBoxEnabled() {
    StudioFlags.PROFILER_TRACEBOX.override(true)
    assertTrue(IntellijProfilerServices.FeatureConfigProd().isTraceboxEnabled)
  }

  @Test
  fun featureFlagConfigTraceBoxDisabled() {
    StudioFlags.PROFILER_TRACEBOX.override(false)
    assertFalse(IntellijProfilerServices.FeatureConfigProd().isTraceboxEnabled)
  }
}