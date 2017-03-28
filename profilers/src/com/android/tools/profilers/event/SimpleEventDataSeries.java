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

import com.android.tools.adtui.model.*;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profilers.ProfilerClient;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class SimpleEventDataSeries implements DataSeries<EventAction<EventAction.Action, EventActionType>> {

  private static final int ACTION_DOWN = 0;
  private static final int ACTION_UP = 1;

  @NotNull
  private ProfilerClient myClient;
  private final int myProcessId;

  public SimpleEventDataSeries(@NotNull ProfilerClient client, int id) {
    myClient = client;
    myProcessId = id;
  }

  @Override
  public ImmutableList<SeriesData<EventAction<EventAction.Action, EventActionType>>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    List<SeriesData<EventAction<EventAction.Action, EventActionType>>> seriesData = new ArrayList<>();
    EventServiceGrpc.EventServiceBlockingStub eventService = myClient.getEventClient();
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()));
    EventProfiler.SystemDataResponse response = eventService.getSystemData(dataRequestBuilder.build());
    for (EventProfiler.SystemData data : response.getDataList()) {
      EventAction.Action action = EventAction.Action.NONE;
      long actionStart = TimeUnit.NANOSECONDS.toMicros(data.getStartTimestamp());
      long actionEnd = TimeUnit.NANOSECONDS.toMicros(data.getEndTimestamp());
      if (data.getType() == EventProfiler.SystemData.SystemEventType.ROTATION) {
        seriesData.add(new SeriesData<>(actionStart, new EventAction<>(actionStart, actionEnd, action, EventActionType.ROTATION)));
      }
      else {
        seriesData.add(new SeriesData<>(actionStart, new EventAction<>(actionStart, actionEnd, action, EventActionType.TOUCH)));
      }
    }
    return ContainerUtil.immutableList(seriesData);
  }
}
