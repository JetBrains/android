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

final class AllocationSamplingRateDurationData implements DurationData {
  @NotNull private final AllocationSamplingRateEvent myOldRateEvent;
  @NotNull private final AllocationSamplingRateEvent myNewRateEvent;
  private final long myDurationUs;

  public AllocationSamplingRateDurationData(@NotNull AllocationSamplingRateEvent oldRateEvent,
                                            @NotNull AllocationSamplingRateEvent newRateEvent) {
    myOldRateEvent = oldRateEvent;
    myNewRateEvent = newRateEvent;
    myDurationUs = TimeUnit.NANOSECONDS.toMicros(newRateEvent.getTimestamp() - oldRateEvent.getTimestamp());
  }

  @NotNull
  public AllocationSamplingRateEvent getOldRateEvent() {
    return myOldRateEvent;
  }

  @NotNull
  public AllocationSamplingRateEvent getNewRateEvent() {
    return myNewRateEvent;
  }

  @Override
  public long getDurationUs() {
    return myDurationUs;
  }
}
