/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.NativeAllocationSampleCaptureObject;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Helper class to query for native allocation recordings and convert them to capture objects.
 */
class NativeAllocationSamplesSeries extends CaptureDataSeries<CaptureObject> {
  @NotNull private final MemoryProfilerStage myStage;

  NativeAllocationSamplesSeries(@NotNull ProfilerClient client,
                                @NotNull Common.Session session,
                                @NotNull FeatureTracker featureTracker,
                                @NotNull MemoryProfilerStage stage) {
    super(client, session, featureTracker);
    myStage = stage;
  }

  @Override
  public List<SeriesData<CaptureDurationData<CaptureObject>>> getDataForRange(Range range) {
    List<Memory.MemoryNativeSampleData> infos =
      MemoryProfiler.getNativeHeapSamplesForSession(myClient, mySession, range);
    List<SeriesData<CaptureDurationData<CaptureObject>>> seriesData = new ArrayList<>();
    for (Memory.MemoryNativeSampleData info : infos) {
      seriesData.add(new SeriesData<>(
        getHostTime(info.getStartTime()),
        new CaptureDurationData<>(
          getDurationUs(info.getStartTime(), info.getEndTime()), false, false,
          new CaptureEntry<>(
            info,
            () -> new NativeAllocationSampleCaptureObject(myClient, mySession, info, myStage)))));
    }

    return seriesData;
  }
}
