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
package com.android.tools.profilers.event;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profilers.ProfilerClient;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ActivityEventDataSeries implements DataSeries<EventAction<EventAction.ActivityAction, String>> {

  @NotNull
  private Map<Integer, Long> myActiveActivites = new HashMap<>();

  @NotNull
  private ProfilerClient myClient;
  private final int myProcessId;

  public ActivityEventDataSeries(@NotNull ProfilerClient client, int id) {
    myClient = client;
    myProcessId = id;
  }

  @Override
  public ImmutableList<SeriesData<EventAction<EventAction.ActivityAction, String>>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    List<SeriesData<EventAction<EventAction.ActivityAction, String>>> seriesData = new ArrayList<>();
    EventServiceGrpc.EventServiceBlockingStub eventService = myClient.getEventClient();
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()));
    EventProfiler.EventDataResponse response = eventService.getData(dataRequestBuilder.build());

    for (EventProfiler.EventProfilerData data : response.getDataList()) {
      long actionStart = TimeUnit.NANOSECONDS.toMicros(data.getBasicInfo().getEndTimestamp());
      long eventTimestamp = actionStart;
      long actionEnd = 0;
      if (data.getDataCase() != EventProfiler.EventProfilerData.DataCase.ACTIVITY_DATA) {
        continue;
      }

      EventProfiler.ActivityEventData activity = data.getActivityData();
      EventAction.ActivityAction action = EventAction.ActivityAction.NONE;
      switch (activity.getActivityState()) {
        case RESUMED:
          action = EventAction.ActivityAction.ACTIVITY_STARTED;
          myActiveActivites.put(activity.getActivityHash(), actionStart);
          break;
        case PAUSED:
          // Depending on when we attach the perfd process we sometimes get an activity completed
          // without having the associated activity started action. This can cause us to attempt
          // and close an activity without actuaully knowing when the activity started.
          // TODO: This is somewhat of a hack, and this should be removed by telling the StackedEventComponent how to handle
          // an activity completed without a started event. Note until I merge fragments, the same issue potentially exist there.
          if (myActiveActivites.containsKey(activity.getActivityHash())) {
            action = EventAction.ActivityAction.ACTIVITY_COMPLETED;
            actionEnd = actionStart;
            actionStart = myActiveActivites.get(activity.getActivityHash());
          }
          break;
      }

      if (action != EventAction.ActivityAction.NONE) {
        seriesData.add(new SeriesData<>(eventTimestamp, new EventAction(actionStart, actionEnd, action, activity.getName())));
      }
    }

    return ContainerUtil.immutableList(seriesData);
  }
}
