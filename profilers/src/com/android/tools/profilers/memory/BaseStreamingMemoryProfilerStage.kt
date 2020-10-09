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
package com.android.tools.profilers.memory

import com.android.tools.profilers.StudioProfilers

/**
 * This class implements common functionalities of a memory stage with a timeline
 */
abstract class BaseStreamingMemoryProfilerStage(profilers: StudioProfilers,
                                                captureObjectLoader: CaptureObjectLoader = CaptureObjectLoader())
      : BaseMemoryProfilerStage(profilers, captureObjectLoader) {

  companion object {
    @JvmField
    val DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE = LiveAllocationSamplingMode.SAMPLED
  }

  enum class LiveAllocationSamplingMode(val value: Int, val displayName: String) {
    NONE(0, "None"),        // 0 is a special value for disabling tracking.
    SAMPLED(10, "Sampled"), // Sample every 10 allocations
    FULL(1, "Full");        // Sample every allocation

    companion object {
      private val SamplingRateMap = values().map { it.value to it }.toMap()
      private val NameMap = values().map { it.displayName to it }.toMap()

      @JvmStatic
      fun getModeFromFrequency(frequency: Int): LiveAllocationSamplingMode =
        SamplingRateMap[frequency] ?: DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE

      @JvmStatic
      fun getModeFromDisplayName(displayName: String): LiveAllocationSamplingMode =
        NameMap[displayName] ?: DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE
    }
  }
}