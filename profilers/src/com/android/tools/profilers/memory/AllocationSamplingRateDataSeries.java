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
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public final class AllocationSamplingRateDataSeries implements DataSeries<AllocationSamplingRateDurationData> {
  @NotNull private final ProfilerClient myClient;
  @NotNull private final Common.Session mySession;
  private final boolean myNewPipeline;

  public AllocationSamplingRateDataSeries(@NotNull ProfilerClient client, @NotNull Common.Session session, @NotNull boolean newPipeline) {
    myClient = client;
    mySession = session;
    myNewPipeline = newPipeline;
  }

  @Override
  public List<SeriesData<AllocationSamplingRateDurationData>> getDataForRange(Range range) {
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)range.getMin());
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)range.getMax());

    if (!myNewPipeline) {
      return getLegacyData(rangeMin, rangeMax);
    }

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

  private List<SeriesData<AllocationSamplingRateDurationData>> getLegacyData(long rangeMinNs, long rangeMaxNs) {
    // TODO(b/113703171): Query only sampling rate events needed to construct range data.
    MemoryRequest.Builder dataRequestBuilder = MemoryRequest
      .newBuilder()
      .setSession(mySession)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE);
    List<AllocationSamplingRateEvent> events =
      myClient.getMemoryClient().getJvmtiData(dataRequestBuilder.build()).getAllocSamplingRateEventsList();

    // MemoryService returns all sampling rate events despite the start/end parameters. We need to filter out those out of range and
    // construct duration data from point data.
    List<SeriesData<AllocationSamplingRateDurationData>> seriesData = new ArrayList<>();
    AllocationSamplingRateEvent prevEvent = null;
    for (int i = 0; i < events.size(); i++) {
      AllocationSamplingRateEvent currEvent = events.get(i);
      // Event is after our request window so skip the rest.
      if (currEvent.getTimestamp() > rangeMaxNs) {
        break;
      }

      // If this is the last event. Set the duration to be Long.MAX_VALUE, otherwise it will be the timestamp difference between the
      // next and current events.
      AllocationSamplingRateEvent nextRateEvent = i == events.size() - 1 ? null : events.get(i + 1);
      long durationUs = nextRateEvent == null ? Long.MAX_VALUE :
                        TimeUnit.NANOSECONDS.toMicros(nextRateEvent.getTimestamp() - currEvent.getTimestamp());

      // Only add events that fall within the query range.
      if (nextRateEvent == null || nextRateEvent.getTimestamp() > rangeMinNs) {
        seriesData.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(currEvent.getTimestamp()),
                                        new AllocationSamplingRateDurationData(durationUs,
                                                                               prevEvent != null ? prevEvent.getSamplingRate() : null,
                                                                               currEvent.getSamplingRate())));
      }
      prevEvent = currEvent;
    }

    return seriesData;
  }
}
