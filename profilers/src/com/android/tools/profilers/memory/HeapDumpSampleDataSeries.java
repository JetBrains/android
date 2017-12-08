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
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class HeapDumpSampleDataSeries extends CaptureDataSeries<CaptureObject> {
  public HeapDumpSampleDataSeries(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                                  @Nullable Common.Session session,
                                  @NotNull RelativeTimeConverter converter,
                                  @NotNull FeatureTracker featureTracker) {
    super(client, session, converter, featureTracker);
  }

  @Override
  public List<SeriesData<CaptureDurationData<CaptureObject>>> getDataForXRange(Range xRange) {
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());
    MemoryProfiler.ListHeapDumpInfosResponse response = myClient.listHeapDumpInfos(
      MemoryProfiler.ListDumpInfosRequest.newBuilder().setSession(mySession).setStartTime(rangeMin).setEndTime(rangeMax).build());

    List<SeriesData<CaptureDurationData<CaptureObject>>> seriesData = new ArrayList<>();
    for (MemoryProfiler.HeapDumpInfo info : response.getInfosList()) {
      seriesData.add(new SeriesData<>(
        getHostTime(info.getStartTime()),
        new CaptureDurationData<>(
          getDurationUs(info.getStartTime(), info.getEndTime()), false, false,
          new CaptureEntry<>(
            info,
            () -> new HeapDumpCaptureObject(myClient, mySession, info, null, myConverter, myFeatureTracker)))));
    }

    return seriesData;
  }
}
