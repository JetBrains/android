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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public final class AllocationSamplingRateDataSeries implements DataSeries<AllocationSamplingRateDurationData> {
  @NotNull private final ProfilerClient myClient;
  @NotNull private final Common.Session mySession;

  public AllocationSamplingRateDataSeries(@NotNull ProfilerClient client, @NotNull Common.Session session) {
    myClient = client;
    mySession = session;
  }

  @Override
  public List<SeriesData<AllocationSamplingRateDurationData>> getDataForRange(Range range) {
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)range.getMin());
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)range.getMax());

    Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(mySession.getStreamId())
      .setPid(mySession.getPid())
      .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
      .setFromTimestamp(rangeMin)
      .setToTimestamp(rangeMax)
      .build();
    Transport.GetEventGroupsResponse response = myClient.getTransportClient().getEventGroups(request);

    assert response.getGroupsCount() <= 1;
    if (response.getGroupsCount() == 0) {
      return new ArrayList<>();
    }

    Transport.EventGroup group = response.getGroups(0);
    List<SeriesData<AllocationSamplingRateDurationData>> seriesData = new ArrayList<>();
    for (int i = 0; i < group.getEventsCount(); i++) {
      Common.Event currEvent = group.getEvents(i);
      // Event is after our request window so skip the rest.
      if (currEvent.getTimestamp() > rangeMax) {
        break;
      }

      Common.Event prevEvent = i == 0 ? null : group.getEvents(i - 1);
      Common.Event nextEvent = i == group.getEventsCount() - 1 ? null : group.getEvents(i + 1);

      // Only add events that fall within the query range.
      if (nextEvent == null || nextEvent.getTimestamp() > rangeMin) {
        long durationUs = nextEvent == null ? Long.MAX_VALUE :
                          TimeUnit.NANOSECONDS.toMicros(nextEvent.getTimestamp() - currEvent.getTimestamp());
        seriesData.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(currEvent.getTimestamp()),
                                        new AllocationSamplingRateDurationData(durationUs,
                                                                               prevEvent != null
                                                                               ? prevEvent.getMemoryAllocSampling()
                                                                               : null,
                                                                               currEvent.getMemoryAllocSampling())));
      }
    }

    return seriesData;
  }
}
