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
package com.android.tools.profilers.cpu

import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ProfilerInEditorUtilsTest {

  private lateinit var ideServices: FakeIdeProfilerServices

  @Before
  fun setUp() {
    ideServices = FakeIdeProfilerServices()
  }

  @Test
  fun javaKotlinAllocations_differentiatesLegacyVsLiveTracking() {
    val featureConfig = ideServices.featureConfig

    // Case 1: Legacy flag enabled, Live flag disabled
    ideServices.setJavaKotlinAllocationsLegacyTraceInEditorEnabled(true)
    ideServices.setJavaKotlinAllocationsInEditorEnabled(false)

    // Legacy allocations should be editor-enabled for offline viewers, but never as a live task
    assertThat(ProfilerInEditorUtils.isEditorEnabled(featureConfig, ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, isLegacyAllocations = true))
      .isTrue()
    assertThat(
        ProfilerInEditorUtils.isLiveTaskInEditorEnabled(featureConfig, ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, isLegacyAllocations = true)
      )
      .isFalse()

    // Live allocations should not be editor-enabled when live flag is disabled
    assertThat(ProfilerInEditorUtils.isEditorEnabled(featureConfig, ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, isLegacyAllocations = false))
      .isFalse()
    assertThat(
        ProfilerInEditorUtils.isLiveTaskInEditorEnabled(
          featureConfig,
          ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
          isLegacyAllocations = false,
        )
      )
      .isFalse()

    // Case 2: Legacy flag disabled, Live flag enabled
    ideServices.setJavaKotlinAllocationsLegacyTraceInEditorEnabled(false)
    ideServices.setJavaKotlinAllocationsInEditorEnabled(true)

    // Legacy allocations should not be editor-enabled
    assertThat(ProfilerInEditorUtils.isEditorEnabled(featureConfig, ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, isLegacyAllocations = true))
      .isFalse()
    assertThat(
        ProfilerInEditorUtils.isLiveTaskInEditorEnabled(featureConfig, ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, isLegacyAllocations = true)
      )
      .isFalse()

    // Live allocations should be editor-enabled as a live streaming task
    assertThat(ProfilerInEditorUtils.isEditorEnabled(featureConfig, ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, isLegacyAllocations = false))
      .isTrue()
    assertThat(
        ProfilerInEditorUtils.isLiveTaskInEditorEnabled(
          featureConfig,
          ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
          isLegacyAllocations = false,
        )
      )
      .isTrue()
  }

  @Test
  fun isLiveTaskInEditorEnabled_returnsFalseForNonLiveTasks() {
    ideServices.setSystemTraceInEditorEnabled(true)
    ideServices.setMethodTraceInEditorEnabled(true)
    ideServices.setCallstackSampleTraceInEditorEnabled(true)
    ideServices.setHeapDumpTraceInEditorEnabled(true)
    ideServices.setNativeAllocationsTraceInEditorEnabled(true)

    val featureConfig = ideServices.featureConfig
    assertThat(ProfilerInEditorUtils.isLiveTaskInEditorEnabled(featureConfig, ProfilerTaskType.SYSTEM_TRACE)).isFalse()
    assertThat(ProfilerInEditorUtils.isLiveTaskInEditorEnabled(featureConfig, ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)).isFalse()
    assertThat(ProfilerInEditorUtils.isLiveTaskInEditorEnabled(featureConfig, ProfilerTaskType.CALLSTACK_SAMPLE)).isFalse()
    assertThat(ProfilerInEditorUtils.isLiveTaskInEditorEnabled(featureConfig, ProfilerTaskType.HEAP_DUMP)).isFalse()
    assertThat(ProfilerInEditorUtils.isLiveTaskInEditorEnabled(featureConfig, ProfilerTaskType.NATIVE_ALLOCATIONS)).isFalse()
  }
}
