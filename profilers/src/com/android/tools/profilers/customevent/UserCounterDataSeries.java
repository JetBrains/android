/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.customevent;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * DataSeries that keeps track of how many user events are happening at a given time, separated into buckets of 500ms.
 */
public class UserCounterDataSeries implements DataSeries<Long> {

  private final static long COUNTER_BUCKET = TimeUnit.MILLISECONDS.toNanos(500);
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myClient;
  @NotNull private final StreamingTimeline myTimeline;
  private final long myStreamId;
  private final int myPid;
  private final long mySessionStartTimestamp;


  public UserCounterDataSeries(@NotNull TransportServiceGrpc.TransportServiceBlockingStub client,
                               @NotNull StudioProfilers profilers) {
    myClient = client;
    myTimeline = profilers.getTimeline();
    myStreamId = profilers.getSession().getStreamId();
    myPid = profilers.getSession().getPid();
    mySessionStartTimestamp = profilers.getSession().getStartTimestamp();
  }

  @Override
  public List<SeriesData<Long>> getDataForRange(Range range) {
    if (range.isEmpty()) {
      return new ArrayList<>();
    }

    long minNs = TimeUnit.MICROSECONDS.toNanos((long)range.getMin());
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)range.getMax());

    // Buckets will always be calculated with the starting point as the start timestamp.
    // This means that minNs and maxNs might fall in-between a bucket, and we want to take all data in that bucket into account.

    //          | -----|-------------- | ------------------- | ---------------|----- |
    // bucketMin    minNs                                                 maxNs   bucketMax
    //          |                                                                    |
    //          | <----- bucket -----> | <----- bucket ----> | <------ bucket -----> |

    long minRangeBucketStart = minNs - ((minNs - mySessionStartTimestamp) % COUNTER_BUCKET);
    long maxRangeBucketEnd = maxNs + (COUNTER_BUCKET - ((maxNs - minRangeBucketStart) % COUNTER_BUCKET));

    Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(myStreamId)
      .setPid(myPid)
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setFromTimestamp(minRangeBucketStart)
      .setToTimestamp(maxRangeBucketEnd)
      .build();
    Transport.GetEventGroupsResponse response = myClient.getEventGroups(request);

    // Bucket's start time mapped to the count of elements within a given bucket.
    TreeMap<Long, Long> bucketStartToCount = new TreeMap<>();

    // Add in the minimum, which will either be the range minimum or data minimum depending on where the range starts.
    // This will be the starting point for where the state chart will be drawn.
    long minDataNs = myTimeline.getDataStartTimeNs();
    long intersectMin = minNs < minDataNs ? minDataNs : minNs;
    bucketStartToCount.put(intersectMin, 0L);

    // Check to make sure that the start of the bucket does not fall out of bounds of the data min, which is needed for legend accuracy.
    long startTimestamp = minRangeBucketStart > minDataNs ? minRangeBucketStart : minDataNs;

    for (Transport.EventGroup group : response.getGroupsList()) {
      if (group.getEventsCount() > 0) {
        for (Common.Event event : group.getEventsList()) {
          long timestamp = event.getTimestamp();

          // Check that event is within range bounds.
          // Note: This check is necessary for calculating the correct legend value, which is calculated by using all the data that is
          // returned from this method. Data should only be included for the buckets within the range that is being looked at.
          if (timestamp >= startTimestamp && timestamp <= maxRangeBucketEnd) {

            // Start of the bucket given this event's timestamp.
            long eventBucketStart = timestamp - ((timestamp - minRangeBucketStart) % COUNTER_BUCKET);

            // Start of the bucket. If start time is below the range minimum, then keep the start as the minimum.
            long initial = eventBucketStart < minNs ? minNs : eventBucketStart;
            Long countStart = bucketStartToCount.get(initial);
            bucketStartToCount.put(initial, countStart == null ? 1 : countStart + 1);

            // End of the bucket. If the end time exceeds the range maximum, end time is not needed.
            if (eventBucketStart + COUNTER_BUCKET <= maxNs) {
              Long countEnd = bucketStartToCount.get(eventBucketStart + COUNTER_BUCKET);
              bucketStartToCount.put(eventBucketStart + COUNTER_BUCKET, countEnd == null ? 0 : countEnd);
            }
          }
        }
      }
    }

    List<SeriesData<Long>> userCounterData = new ArrayList<>();
    for (Map.Entry<Long, Long> entry : bucketStartToCount.entrySet()) {
      userCounterData.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(entry.getKey()), entry.getValue()));
    }

    return userCounterData;
  }
}

