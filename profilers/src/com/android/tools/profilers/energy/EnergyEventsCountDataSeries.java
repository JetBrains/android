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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profilers.UnifiedEventDataSeries;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A data series to count how many energy events are merged into one.
 * <p>
 * For example, events like so:
 * <p>
 * <pre>
 *
 *    [========) <- terminal timestamp not included
 *        [===========)
 *                          [=====)
 *                               [==)
 *                                      [========)
 * </pre>
 * <p>
 * would be counted as
 * <p>
 * <pre>
 *   011112222211111110000001111121100001111111110
 * </pre>
 */
public class EnergyEventsCountDataSeries implements DataSeries<Long> {
  private final TransportServiceGrpc.TransportServiceBlockingStub myClient;
  private final long myStreamId;
  private final int myPid;
  private final Predicate<EnergyDuration.Kind> myKindFilter;

  /**
   * @param kindFilter only count event for which the kind predicate evaluates to true.
   */
  public EnergyEventsCountDataSeries(TransportServiceGrpc.TransportServiceBlockingStub client,
                                     long streamId,
                                     int pid,
                                     Predicate<EnergyDuration.Kind> kindFilter) {
    myClient = client;
    myStreamId = streamId;
    myPid = pid;
    myKindFilter = kindFilter;
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(Range xRange) {
    long minUs = (long)xRange.getMin();
    long maxUs = (long)xRange.getMax();
    long minNs = TimeUnit.MICROSECONDS.toNanos(minUs);
    long maxNs = TimeUnit.MICROSECONDS.toNanos(maxUs);

    Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(myStreamId)
      .setPid(myPid)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setGroupId(UnifiedEventDataSeries.DEFAULT_GROUP_ID)
      .setFromTimestamp(minNs)
      .setToTimestamp(maxNs)
      .build();
    Transport.GetEventGroupsResponse response = myClient.getEventGroups(request);

    long count = response.getGroupsList().stream()
      .filter(group -> myKindFilter.test(EnergyDuration.Kind.from(group.getEvents(0).getEnergyEvent())))
      .count();
    return Arrays.asList(new SeriesData<>(minUs, count), new SeriesData<>(maxUs, count));
  }
}
