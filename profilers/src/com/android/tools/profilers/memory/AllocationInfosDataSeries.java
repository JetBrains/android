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
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject;
import com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AllocationInfosDataSeries extends CaptureDataSeries<CaptureObject> {
  @Nullable private MemoryProfilerStage myStage;

  public AllocationInfosDataSeries(@NotNull ProfilerClient client,
                                   @NotNull Common.Session session,
                                   @NotNull FeatureTracker featureTracker,
                                   @Nullable MemoryProfilerStage stage) {
    super(client, session, featureTracker);
    myStage = stage;
  }

  @Override
  public List<SeriesData<CaptureDurationData<CaptureObject>>> getDataForRange(Range range) {
    List<Memory.AllocationsInfo> infos =
      MemoryProfiler.getAllocationInfosForSession(myClient, mySession, range, myStage.getStudioProfilers().getIdeServices());

    List<SeriesData<CaptureDurationData<CaptureObject>>> seriesData = new ArrayList<>();
    for (Memory.AllocationsInfo info : infos) {
      long startTimeNs = info.getStartTime();
      long durationUs = getDurationUs(startTimeNs, info.getEndTime());

      seriesData.add(
        new SeriesData<>(
          getHostTime(startTimeNs),
          new CaptureDurationData<>(durationUs, !info.getLegacy(), !info.getLegacy(), new CaptureEntry<>(
            info,
            () -> {
              if (info.getLegacy()) {
                return new LegacyAllocationCaptureObject(myClient, mySession, info, myFeatureTracker);
              }
              else {
                return new LiveAllocationCaptureObject(myClient, mySession, startTimeNs, null, myStage);
              }
            }))));
    }
    return seriesData;
  }
}
