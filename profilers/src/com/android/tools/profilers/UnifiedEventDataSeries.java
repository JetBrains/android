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
package com.android.tools.profilers;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Generic numeric (Long) data series that allows the caller to customize which field within the {@link Common.Event} to use when when
 * querying for data.
 */
public class UnifiedEventDataSeries implements DataSeries<Long> {

  public static final int DEFAULT_GROUP_ID = Common.Event.EventGroupIds.INVALID_VALUE;

  @NotNull private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myClient;
  @NotNull private final Common.Session mySession;
  @NotNull private final Common.Event.Kind myKind;
  private final int myGroupId;
  @NotNull private final Function<Common.Event, Long> myDataExtractor;

  /**
   * @param client        the grpc client to request data from.
   * @param session       the session to query.
   * @param kind          the data kind ot query.
   * @param groupId       the group id within the data kind to query. If the data don't have group distinction, use {@link DEFAULT_GROUP_ID}.
   * @param dataExtractor the function to extract the numeric field from an event to build the data series with.
   */
  public UnifiedEventDataSeries(@NotNull ProfilerServiceGrpc.ProfilerServiceBlockingStub client,
                                @NotNull Common.Session session,
                                @NotNull Common.Event.Kind kind,
                                int groupId,
                                @NotNull Function<Common.Event, Long> dataExtractor) {
    myClient = client;
    mySession = session;
    myKind = kind;
    myGroupId = groupId;
    myDataExtractor = dataExtractor;
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(Range xRangeUs) {
    // TODO b/118319729 remove the one second buffer after our request supports buffering options.
    long minNs = TimeUnit.MICROSECONDS.toNanos((long)xRangeUs.getMin()) - TimeUnit.SECONDS.toNanos(1);
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)xRangeUs.getMax()) + TimeUnit.SECONDS.toNanos(1);

    Profiler.GetEventGroupsRequest request = Profiler.GetEventGroupsRequest.newBuilder()
                                                                           .setSessionId(mySession.getSessionId())
                                                                           .setKind(myKind)
                                                                           .setGroupId(myGroupId)
                                                                           .setFromTimestamp(minNs)
                                                                           .setToTimestamp(maxNs)
                                                                           .build();
    List<SeriesData<Long>> series = new ArrayList<>();
    Profiler.GetEventGroupsResponse response = myClient.getEventGroups(request);
    // We don't expect more than one data group in our numeric data series. This is to avoid having to sort the data from multiple groups
    // after they are added to the list. We can re-evaluate if the need arises.
    assert response.getGroupsCount() <= 1;
    for (Profiler.EventGroup group : response.getGroupsList()) {
      for (Common.Event event : group.getEventsList()) {
        series.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(event.getTimestamp()), myDataExtractor.apply(event)));
      }
    }
    return series;
  }
}
