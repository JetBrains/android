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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for querying CPU thread data from unified pipeline {@link Common.Event} and extract thread count data.
 */
public class CpuThreadCountDataSeries implements DataSeries<Long> {
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myClient;
  private final long myStreamId;
  private final int myPid;

  public CpuThreadCountDataSeries(@NotNull TransportServiceGrpc.TransportServiceBlockingStub client, long streamId, int pid) {
    myClient = client;
    myStreamId = streamId;
    myPid = pid;
  }

  @Override
  public List<SeriesData<Long>> getDataForRange(Range rangeUs) {
    long minNs = TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin());
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax());

    GetEventGroupsRequest request = GetEventGroupsRequest.newBuilder()
      .setStreamId(myStreamId)
      .setPid(myPid)
      .setKind(Common.Event.Kind.CPU_THREAD)
      .setFromTimestamp(minNs)
      .setToTimestamp(maxNs)
      .build();
    GetEventGroupsResponse response = myClient.getEventGroups(request);

    // Count the first and last events of a thread by timestamp. If the event is terminal, use -1 towards the count to offset the summing
    // phase later.
    //
    // Threads states:
    // +: non terminal
    // @: terminal
    // Timestamp    0  1  2  3  4  5
    // Thread 1        +-----@
    // Thread 2           +-----+--@
    // First Pass   x  1  1 -1  x -1
    // Total Count  0  1  2  1  1  0
    TreeMap<Long, Long> timestampToCountMap = new TreeMap<>();
    for (Transport.EventGroup group : response.getGroupsList()) {
      if (group.getEventsCount() > 0) {
        Common.Event first = group.getEvents(0);
        Common.Event last = group.getEvents(group.getEventsCount() - 1);
        timestampToCountMap.compute(first.getTimestamp(), (timestamp, count) -> count == null ? 1 : count + 1);
        if (last.getIsEnded()) {
          timestampToCountMap.compute(last.getTimestamp(), (timestamp, count) -> count == null ? -1 : count - 1);
        }
      }
    }
    List<SeriesData<Long>> data = new ArrayList<>();
    long total = 0;
    for (Map.Entry<Long, Long> entry : timestampToCountMap.entrySet()) {
      total += entry.getValue();
      data.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(entry.getKey()), total));
    }

    // When no threads are found within the requested range, we add the threads count (0)
    // to both range's min and max. Otherwise we wouldn't add any information to the data series
    // within timeCurrentRangeUs and nothing would be added to the chart.
    if (timestampToCountMap.isEmpty()) {
      data.add(new SeriesData<>((long)rangeUs.getMin(), total));
      data.add(new SeriesData<>((long)rangeUs.getMax(), total));
    }
    // If the last timestamp added to the data series is less than timeCurrentRangeUs.getMax(),
    // we need to replicate the last value in timeCurrentRangeUs.getMax(), so the chart renders this value
    // until the end of the selected range.
    else if (data.get(data.size() - 1).x < rangeUs.getMax()) {
      data.add(new SeriesData<>((long)rangeUs.getMax(), total));
    }

    return data;
  }
}
