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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.UnifiedEventDataSeries;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A class which handles fetching a list of {@link EnergyDuration} instances within a
 * specified range. When the range changes, the list will automatically be updated, and this class
 * will notify any listeners.
 */
public class EnergyEventsFetcher {

  // myAspectObserver cannot be local to prevent early GC
  @SuppressWarnings("FieldCanBeLocal") private final AspectObserver myAspectObserver = new AspectObserver();

  @NotNull private final TransportServiceGrpc.TransportServiceBlockingStub myTransportClient;
  @NotNull private final EnergyServiceGrpc.EnergyServiceBlockingStub myEnergyClient;
  @NotNull private final Common.Session mySession;
  private final boolean myIsUnifiedPipeline;
  @NotNull private final List<Listener> myListeners = new ArrayList<>();

  @NotNull private final Range myRange;

  @Nullable private List<EnergyDuration> myDurationList;

  public EnergyEventsFetcher(@NotNull ProfilerClient profilerClient,
                             @NotNull Common.Session session,
                             @NotNull Range range,
                             boolean isUnifiedPipeline) {
    myTransportClient = profilerClient.getTransportClient();
    myEnergyClient = profilerClient.getEnergyClient();
    mySession = session;
    myIsUnifiedPipeline = isUnifiedPipeline;
    myRange = range;
    myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::handleRangeUpdated);
    handleRangeUpdated();
  }

  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
    if (myDurationList != null) {
      fireListeners(myDurationList);
    }
  }

  private void handleRangeUpdated() {
    List<EnergyDuration> durationList = new ArrayList<>();
    long minNS = TimeUnit.MICROSECONDS.toNanos((long)myRange.getMin());
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)myRange.getMax());

    if (!myRange.isEmpty()) {
      if (myIsUnifiedPipeline) {
        Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
          .setStreamId(mySession.getStreamId())
          .setPid(mySession.getPid())
          .setKind(Common.Event.Kind.ENERGY_EVENT)
          .setGroupId(UnifiedEventDataSeries.DEFAULT_GROUP_ID)
          .setFromTimestamp(minNS)
          .setToTimestamp(maxNs)
          .build();
        Transport.GetEventGroupsResponse response = myTransportClient.getEventGroups(request);
        for (Transport.EventGroup eventGroup : response.getGroupsList()) {
          durationList.add(new EnergyDuration(eventGroup.getEventsList()));
        }
      } else {
        EnergyProfiler.EnergyRequest request = EnergyProfiler.EnergyRequest.newBuilder()
          .setSession(mySession)
          .setStartTimestamp(minNS)
          .setEndTimestamp(maxNs)
          .build();
        List<Common.Event> eventList = myEnergyClient.getEvents(request).getEventsList();
        List<EnergyDuration> partialDurations = EnergyDuration.groupById(eventList);
        for (EnergyDuration partialDuration : partialDurations) {
          EnergyProfiler.EnergyEventGroupRequest eventGroupRequest = EnergyProfiler.EnergyEventGroupRequest.newBuilder()
            .setSession(mySession)
            .setEventId(partialDuration.getEventList().get(0).getGroupId())
            .build();
          durationList.add(new EnergyDuration(myEnergyClient.getEventGroup(eventGroupRequest).getEventsList()));
        }
      }
    }

    if (myDurationList == null || !myDurationList.equals(durationList)) {
      myDurationList = durationList;
      fireListeners(myDurationList);
    }
  }

  private void fireListeners(@NotNull List<EnergyDuration> list) {
    for (Listener l : myListeners) {
      l.onUpdated(list);
    }
  }

  public interface Listener {
    void onUpdated(@NotNull List<EnergyDuration> durationList);
  }
}
