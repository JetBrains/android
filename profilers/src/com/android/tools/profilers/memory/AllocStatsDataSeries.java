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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory.MemoryAllocStatsData;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

public final class AllocStatsDataSeries implements DataSeries<Long> {
  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final ProfilerClient myClient;
  @NotNull private final Common.Session mySession;
  @SuppressWarnings("FieldCanBeLocal") @NotNull private final AspectObserver myObserver;
  private boolean myIsAgentAttached = false;

  @NotNull
  private Function<MemoryAllocStatsData, Long> myFilter;

  public AllocStatsDataSeries(@NotNull StudioProfilers profilers,
                              @NotNull Function<MemoryAllocStatsData, Long> filter) {
    myProfilers = profilers;
    myClient = profilers.getClient();
    mySession = myProfilers.getSession();
    myFilter = filter;

    myObserver = new AspectObserver();
    myProfilers.addDependency(myObserver).onChange(ProfilerAspect.AGENT, this::agentStatusChanged);
    agentStatusChanged();
  }

  @Override
  public List<SeriesData<Long>> getDataForRange(@NotNull Range rangeUs) {
    if (!myIsAgentAttached) {
      return Collections.emptyList();
    }

    Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(mySession.getStreamId())
      .setPid(mySession.getPid())
      .setKind(Common.Event.Kind.MEMORY_ALLOC_STATS)
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin()))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax()))
      .build();
    Transport.GetEventGroupsResponse response = myClient.getTransportClient().getEventGroups(request);

    // No group ids for allocation stats so there shouldn't be more than one group.
    assert response.getGroupsCount() <= 1;

    List<SeriesData<Long>> seriesData = new ArrayList<>();
    if (response.getGroupsCount() > 0) {

      response.getGroups(0).getEventsList().forEach(event -> {
        long dataTimestamp = TimeUnit.NANOSECONDS.toMicros(event.getTimestamp());
        seriesData.add(new SeriesData<>(dataTimestamp, myFilter.apply(event.getMemoryAllocStats())));
      });
    }

    return seriesData;
  }

  private void agentStatusChanged() {
    myIsAgentAttached = myProfilers.isAgentAttached();
  }
}
