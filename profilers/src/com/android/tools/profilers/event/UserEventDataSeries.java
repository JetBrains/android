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
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.KeyboardAction;
import com.android.tools.adtui.model.event.KeyboardData;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profilers.StudioProfilers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class UserEventDataSeries implements DataSeries<EventAction<UserEvent>> {
  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final Common.Session mySession;

  public UserEventDataSeries(@NotNull StudioProfilers profilers) {
    myProfilers = profilers;
    mySession = profilers.getSession();
  }

  @Override
  public List<SeriesData<EventAction<UserEvent>>> getDataForRange(@NotNull Range timeCurrentRangeUs) {
    return getTransportData(timeCurrentRangeUs);
  }

  @NotNull
  private List<SeriesData<EventAction<UserEvent>>> getTransportData(@NotNull Range rangeUs) {
    List<SeriesData<EventAction<UserEvent>>> series = new ArrayList<>();
    GetEventGroupsRequest request = GetEventGroupsRequest.newBuilder()
      .setKind(Common.Event.Kind.INTERACTION)
      .setStreamId(mySession.getStreamId())
      .setPid(mySession.getPid())
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin()))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax()))
      .build();
    GetEventGroupsResponse response = myProfilers.getClient().getTransportClient().getEventGroups(request);
    for (EventGroup group : response.getGroupsList()) {
      Common.Event startEvent = group.getEvents(0);
      long actionStart = TimeUnit.NANOSECONDS.toMicros(startEvent.getTimestamp());
      switch (startEvent.getInteraction().getType()) {
        // Both rotation and key are single-timestamped events.
        case ROTATION:
          series.add(new SeriesData<>(actionStart, new EventAction<>(actionStart, actionStart, UserEvent.ROTATION)));
          break;
        case KEY:
          series.add(
            new SeriesData<>(actionStart,
                             new KeyboardAction(actionStart, actionStart, new KeyboardData(startEvent.getInteraction().getEventData()))));
          break;
        case TOUCH:
          long actionEnd = group.getEventsCount() == 1
                           ? (long)rangeUs.getMax()
                           : TimeUnit.NANOSECONDS.toMicros(group.getEvents(group.getEventsCount() - 1).getTimestamp());
          series.add(new SeriesData<>(actionStart, new EventAction<>(actionStart, actionEnd, UserEvent.TOUCH)));
          break;
        case UNSPECIFIED:
        case UNRECOGNIZED:
          break;
      }
    }
    Collections.sort(series, Comparator.comparingLong(data -> data.x));
    return series;
  }
}
