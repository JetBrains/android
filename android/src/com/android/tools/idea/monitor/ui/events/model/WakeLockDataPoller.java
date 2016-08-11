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

import com.android.ddmlib.Log;
import com.android.tools.adtui.StackedEventComponent;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.idea.monitor.datastore.DataAdapterImpl;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import gnu.trove.TLongArrayList;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WakeLockDataPoller extends Poller {
  private static final String TAG = WakeLockDataPoller.class.getName();
  private long myLatestEventTimestampNs;
  private int myPid;
  private EnergyServiceGrpc.EnergyServiceBlockingStub myEnergyService;
  private TLongArrayList mySystemTime = new TLongArrayList();
  private List<EventAction<StackedEventComponent.Action, String>> myWakeLockData = new ArrayList<>();
  private Map<String, Long> myActiveWakeLocks = new HashMap<>();

  public WakeLockDataPoller(SeriesDataStore dataStore, int pid) {
    super(dataStore, POLLING_DELAY_NS);
    myPid = pid;
    dataStore.registerAdapter(SeriesDataType.EVENT_WAKE_LOCK_ACTION, new DataAdapterImpl(mySystemTime, myWakeLockData));
  }

  @Override
  protected void asyncInit() throws StatusRuntimeException {
    myEnergyService = myService.getEnergyService();
    myLatestEventTimestampNs = 1;  // By default empty timestamps are zero, so we put 1 here so it won't return to us empty timestamps.
  }

  @Override
  protected void asyncShutdown() throws StatusRuntimeException {
    // Wake lock poller doesn't involve an on-device poller. No need to do anything.
  }

  @Override
  protected void poll() throws StatusRuntimeException {
    EnergyProfiler.WakeLockDataRequest request = EnergyProfiler.WakeLockDataRequest.newBuilder()
      .setAppId(myPid)
      .setStartTimeExcl(myLatestEventTimestampNs)
      .setEndTimeIncl(Long.MAX_VALUE)
      .build();
    EnergyProfiler.WakeLockDataResponse response = myEnergyService.getWakeLockData(request);

    long latestEventTimeStampNs = 0;
    for (EnergyProfiler.WakeLockEvent event : response.getWakeLockEventsList()) {
      StackedEventComponent.Action action = StackedEventComponent.Action.NONE;
      long eventTimestampUs = TimeUnit.NANOSECONDS.toMicros(event.getTimestamp());
      long actionStart = eventTimestampUs;
      long actionEnd = 0;

      switch (event.getAction()) {
        case ACQUIRED:
          action = StackedEventComponent.Action.ACTIVITY_STARTED; // Using activity enums for now.
          myActiveWakeLocks.put(event.getName(), actionStart);
          break;
        case RELEASED_MANUAL:
          // INTENTIONAL FALL-THROUGH: Currently we only record release without specifying manual or automatic.
          // We should implement some kind of indicator to tell the user if the wake lock ended due to manual release or due to automatic
          // timeout.
        case RELEASED_AUTOMATIC:
          assert myActiveWakeLocks.containsKey(event.getName()); // There should never be a release without an acquire.
          action = StackedEventComponent.Action.ACTIVITY_COMPLETED;
          actionEnd = actionStart;
          actionStart = myActiveWakeLocks.get(event.getName());
          break;
      }

      if (action != StackedEventComponent.Action.NONE) {
        mySystemTime.add(eventTimestampUs);
        myWakeLockData.add(new EventAction<>(actionStart, actionEnd, action, event.getName()));
      }

      if (latestEventTimeStampNs < event.getTimestamp()) {
        latestEventTimeStampNs = event.getTimestamp();
      }
    }

    if (myLatestEventTimestampNs < latestEventTimeStampNs) {
      myLatestEventTimestampNs = latestEventTimeStampNs;
    }
  }
}
