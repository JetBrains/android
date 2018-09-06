/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.DurationData;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AllocationSamplingRateDurationData implements DurationData {
  @Nullable private final AllocationSamplingRateEvent myPreviousRateEvent;
  @NotNull private final AllocationSamplingRateEvent myCurrentRateEvent;
  private final long myDurationUs;

  public AllocationSamplingRateDurationData(long durationUs,
                                            @Nullable AllocationSamplingRateEvent previousRateEvent,
                                            @NotNull AllocationSamplingRateEvent currentRateEvent) {
    myDurationUs = durationUs;
    myPreviousRateEvent = previousRateEvent;
    myCurrentRateEvent = currentRateEvent;
  }

  @Nullable
  public AllocationSamplingRateEvent getPreviousRateEvent() {
    return myPreviousRateEvent;
  }

  @NotNull
  public AllocationSamplingRateEvent getCurrentRateEvent() {
    return myCurrentRateEvent;
  }

  @Override
  public long getDurationUs() {
    return myDurationUs;
  }
}
