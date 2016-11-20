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

import static com.android.tools.profiler.proto.EventProfiler.ActivityStateData.ActivityState.*;

public class ActivityEventDataSeries implements DataSeries<EventAction<EventAction.ActivityAction, String>> {

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
    EventProfiler.ActivityDataResponse response = eventService.getActivityData(dataRequestBuilder.build());

    for (EventProfiler.ActivityData data : response.getDataList()) {
      long actionStart = 0;
      long actionEnd = 0;
      for (int i = 0; i < data.getStateChangesCount(); i++) {
        EventProfiler.ActivityStateData state = data.getStateChanges(i);
        EventAction.ActivityAction action = EventAction.ActivityAction.NONE;

        // Match start states with end states.
        switch (state.getState()) {
          case RESUMED:
            action = EventAction.ActivityAction.ACTIVITY_STARTED;
            actionStart = TimeUnit.NANOSECONDS.toMicros(state.getTimestamp());
            break;
          case PAUSED:
            action = EventAction.ActivityAction.ACTIVITY_COMPLETED;
            actionEnd = TimeUnit.NANOSECONDS.toMicros(state.getTimestamp());
          default:
            break;
        }

        // Create a UI event if we have an end time, or if we got to the end of our list.
        if (actionEnd != 0 || (i == data.getStateChangesCount()-1 && actionStart != 0)) {
          seriesData.add(new SeriesData<>(actionStart, new EventAction(actionStart, actionEnd, action, data.getName())));
          actionEnd = 0;
          actionStart = 0;
        }
      }
    }

    return ContainerUtil.immutableList(seriesData);
  }
}
