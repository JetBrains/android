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
package com.android.tools.idea.monitor.ui.events.model;

import com.android.tools.adtui.SimpleEventComponent;
import com.android.tools.adtui.StackedEventComponent;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.idea.monitor.datastore.DataAdapterImpl;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;
import com.android.tools.idea.monitor.ui.events.view.EventSegment;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventProfilerServiceGrpc;
import gnu.trove.TLongArrayList;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EventDataPoller extends Poller {

  private static final int ACTION_DOWN = 0;
  private static final int ACTION_UP = 1;
  private static final String ACTIVITY_DATA_PROTO_NAME = "ACTIVITY_DATA";
  private static final String FRAGMENT_DATA_PROTO_NAME = "FRAGMENT_DATA";
  private static final String SYSTEM_DATA_PROTO_NAME = "SYSTEM_DATA";

  private long myDataRequestStartTimestampNs;
  private int myPid;
  private EventProfilerServiceGrpc.EventProfilerServiceBlockingStub myEventService;
  private TLongArrayList mySystemTime = new TLongArrayList();
  private TLongArrayList myFragmentTime = new TLongArrayList();
  private TLongArrayList myActivityTime = new TLongArrayList();
  private List<EventAction<StackedEventComponent.Action, String>> myFragmentData = new ArrayList<>();
  private List<EventAction<StackedEventComponent.Action, String>> myActivityData = new ArrayList<>();
  private List<EventAction<SimpleEventComponent.Action, EventSegment.EventActionType>> mySystemData = new ArrayList<>();
  private Map<Integer, Long> myActiveActivites = new HashMap<>();
  private Map<Integer, Long> myActiveFragments = new HashMap<>();
  private long myLastDownEvent;

  public EventDataPoller(@NotNull DeviceProfilerService service, int pid, SeriesDataStore dataStore) {
    super(dataStore, POLLING_DELAY_NS);

    myPid = pid;
    dataStore.registerAdapter(SeriesDataType.EVENT_SIMPLE_ACTION, new DataAdapterImpl(mySystemTime, mySystemData));
    dataStore.registerAdapter(SeriesDataType.EVENT_ACTIVITY_ACTION, new DataAdapterImpl(myActivityTime, myActivityData));
    dataStore.registerAdapter(SeriesDataType.EVENT_FRAGMENT_ACTION, new DataAdapterImpl(myFragmentTime, myFragmentData));
  }

  @Override
  protected void asyncInit() {
    myEventService = myService.getEventService();
    myDataRequestStartTimestampNs = Long.MIN_VALUE;
  }

  @Override
  protected void asyncShutdown() {
  }

  @Override
  protected void poll() {
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setAppId(myPid)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);
    EventProfiler.EventDataResponse response;
    try {
      response = myEventService.getData(dataRequestBuilder.build());
    }
    catch (StatusRuntimeException e) {
      cancel(true);
      return;
    }

    for (EventProfiler.EventProfilerData data : response.getDataList()) {
      myDataRequestStartTimestampNs = data.getBasicInfo().getEndTimestamp();
      long actionStart = TimeUnit.NANOSECONDS.toMicros(myDataRequestStartTimestampNs);
      long eventTimestamp = actionStart;
      long actionEnd = 0;

      // TODO Everything in each if statement would be ideally moved to a data adapter, that way
      // we have a class that can perform the conversion from proto to ui data type.
      if (data.getDataCase().toString().equals(ACTIVITY_DATA_PROTO_NAME)) {
        EventProfiler.ActivityEventData activity = data.getActivityData();
        StackedEventComponent.Action action = StackedEventComponent.Action.NONE;
        switch (activity.getActivityState()) {
          case RESUMED:
            action = StackedEventComponent.Action.ACTIVITY_STARTED;
            myActiveActivites.put(activity.getActivityHash(), actionStart);
            break;
          case PAUSED:
            action = StackedEventComponent.Action.ACTIVITY_COMPLETED;
            actionEnd = actionStart;
            actionStart = myActiveActivites.get(activity.getActivityHash());
            break;
        }
        if (action != StackedEventComponent.Action.NONE) {
          myActivityTime.add(eventTimestamp);
          myActivityData.add(new EventAction<>(actionStart, actionEnd, action, activity.getName()));
        }
      } else if (data.getDataCase().toString().equals(FRAGMENT_DATA_PROTO_NAME)) {
        // TODO: Combine fragment and activity data into one, the data that comes across for fragments is a subset.
        EventProfiler.FragmentEventData fragment = data.getFragmentData();
        StackedEventComponent.Action action = StackedEventComponent.Action.NONE;
        switch (fragment.getFragmentState()) {
          case ADDED:
            action = StackedEventComponent.Action.ACTIVITY_STARTED;
            myActiveFragments.put(fragment.getFragmentHash(), actionStart);
            break;
          case REMOVED:
            action = StackedEventComponent.Action.ACTIVITY_COMPLETED;
            actionEnd = actionStart;
            actionStart = myActiveFragments.get(fragment.getFragmentHash());
            break;
        }
        if (action != StackedEventComponent.Action.NONE) {
          myFragmentTime.add(eventTimestamp);
          myFragmentData.add(new EventAction<>(actionStart, actionEnd, action, fragment.getName()));
        }
      } else if (data.getDataCase().toString().equals(SYSTEM_DATA_PROTO_NAME)) {
        EventProfiler.SystemEventData systemData = data.getSystemData();
        SimpleEventComponent.Action action = SimpleEventComponent.Action.NONE;
        switch (systemData.getActionId()) {
          case ACTION_DOWN:
            action = SimpleEventComponent.Action.DOWN;
            myLastDownEvent = actionStart;
            break;
          case ACTION_UP:
            action = SimpleEventComponent.Action.UP;
            // TODO: Use the down up time in the MotionEvent to set start time for touchstate.
            actionEnd = actionStart;
            actionStart = myLastDownEvent;
            break;
        }
        if( action != SimpleEventComponent.Action.NONE) {
          mySystemTime.add(eventTimestamp);
          mySystemData.add(new EventAction<>(actionStart, actionEnd, action, EventSegment.EventActionType.TOUCH));
        }
      }
    }
  }
}
