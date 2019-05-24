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

import com.android.annotations.NonNull;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * A data series where multiple, separate series are merged into one.
 * <p>
 * For example, events like so:
 *
 * <pre>
 *    [========]
 *        [===========]
 *                          [=====]
 *                               [==]
 *                                      [========]
 * </pre>
 * <p>
 * would be collapsed into:
 *
 * <pre>
 *    [===============]     [=======]   [========]
 * </pre>
 */
public class MergedEnergyEventsDataSeries implements DataSeries<Common.Event> {
  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myClient;
  private final long myStreamId;
  private final int myPid;
  @NonNull private final Predicate<EnergyDuration.Kind> myKindPredicate;

  public MergedEnergyEventsDataSeries(@NotNull TransportServiceGrpc.TransportServiceBlockingStub client,
                                      long streamId,
                                      int pid,
                                      @NotNull Predicate<EnergyDuration.Kind> kindPredicate) {
    myClient = client;
    myStreamId = streamId;
    myPid = pid;
    myKindPredicate = kindPredicate;
  }

  @Override
  public List<SeriesData<Common.Event>> getDataForXRange(Range xRange) {
    long minNs = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());
    List<SeriesData<Common.Event>> destData = new ArrayList<>();
    Set<Long> activeEventGroups = new HashSet<>();

    Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(myStreamId)
      .setPid(myPid)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setFromTimestamp(minNs)
      .setToTimestamp(maxNs)
      .build();
    Transport.GetEventGroupsResponse response = myClient.getEventGroups(request);

    for (Transport.EventGroup group : response.getGroupsList()) {
      if (!myKindPredicate.test(EnergyDuration.Kind.from(group.getEvents(0).getEnergyEvent()))) {
        continue;
      }
      // Here, we are going to combine separate event groups into one. We basically loop through
      // all events (which are in sorted order), and create new, fake event groups on the fly that
      // are a superset of those groups. We keep track of all active event groups (those that have
      // been started but not yet finished), so the superset group starts when we get our first
      // active event, and it ends when we get a terminal event while no other groups are active.
      //
      //   t0   t1   t2   t3   t4   t5
      //    [=========]                  <- Active t0 - t2
      //    |   [===============]        <- Active t1 - t4
      //    |             [=========]    <- Active t3 - t5
      //    |                       |
      //  start                    end
      for (Common.Event event : group.getEventsList()) {
        if (!event.getIsEnded()) {
          if (activeEventGroups.isEmpty()) {
            destData.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(event.getTimestamp()), event));
          }
          activeEventGroups.add(group.getGroupId());
        }
        else {
          activeEventGroups.remove(group.getGroupId());
          if (activeEventGroups.isEmpty()) {
            destData.add(new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(event.getTimestamp()), event));
          }
        }
      }
    }
    return destData;
  }
}
