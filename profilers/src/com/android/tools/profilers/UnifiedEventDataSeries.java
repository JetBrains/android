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
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Generic numeric (Long) data series that allows the caller to customize which field within the {@link Common.Event} to use when when
 * querying for data.
 */
public class UnifiedEventDataSeries<T> implements DataSeries<T> {

  public static final int DEFAULT_GROUP_ID = Common.Event.EventGroupIds.INVALID_VALUE;

  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myClient;
  private final long myStreamId;
  private final int myPid;
  @NotNull private final Common.Event.Kind myKind;
  private final int myGroupId;
  @NotNull private final Function<List<Common.Event>, List<SeriesData<T>>> myDataExtractor;

  /**
   * @param client        the grpc client to request data from.
   * @param streamId
   * @param pid
   * @param kind          the data kind ot query.
   * @param groupId       the group id within the data kind to query. If the data don't have group distinction, use DEFAULT_GROUP_ID.
   * @param dataExtractor the function to extract data from a list of events to build the list of series data.
   */
  public UnifiedEventDataSeries(@NotNull TransportServiceGrpc.TransportServiceBlockingStub client,
                                long streamId,
                                int pid,
                                @NotNull Common.Event.Kind kind,
                                int groupId,
                                @NotNull Function<List<Common.Event>, List<SeriesData<T>>> dataExtractor) {
    myClient = client;
    myStreamId = streamId;
    myPid = pid;
    myKind = kind;
    myGroupId = groupId;
    myDataExtractor = dataExtractor;
  }

  @Override
  public List<SeriesData<T>> getDataForRange(Range rangeUs) {
    GetEventGroupsRequest request = GetEventGroupsRequest.newBuilder()
      .setStreamId(myStreamId)
      .setPid(myPid)
      .setKind(myKind)
      .setGroupId(myGroupId)
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin()))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax()))
      .build();
    GetEventGroupsResponse response = myClient.getEventGroups(request);
    // We don't expect more than one data group in our numeric data series. This is to avoid having to sort the data from multiple groups
    // after they are added to the list. We can re-evaluate if the need arises.
    assert response.getGroupsCount() <= 1;
    if (response.getGroupsCount() == 0) {
      return new ArrayList<>();
    }
    return myDataExtractor.apply(response.getGroups(0).getEventsList());
  }

  /**
   * Helper function that constructs list data extractor from a field extractor for the simple case of extracting one field out of every
   * {@link Common.Event}.
   *
   * @param fieldExtractor a {@link Function} that extracts a Long field from an {@link Common.Event}.
   * @return a {@link Function} that converts a list of events into a list of {@link SeriesData}.
   */
  public static Function<List<Common.Event>, List<SeriesData<Long>>> fromFieldToDataExtractor(Function<Common.Event, Long> fieldExtractor) {
    return events -> events.stream()
      .map(event -> new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(event.getTimestamp()), fieldExtractor.apply(event)))
      .collect(Collectors.toList());
  }
}
