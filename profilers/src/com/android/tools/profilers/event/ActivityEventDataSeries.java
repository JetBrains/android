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
import com.android.tools.adtui.model.event.ActivityAction;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.StackedEventType;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profilers.ProfilerClient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ActivityEventDataSeries implements DataSeries<EventAction<StackedEventType>> {

  @NotNull
  private ProfilerClient myClient;
  private final int myProcessId;
  private final Common.Session mySession;
  private final boolean myFragmentsOnly;

  public ActivityEventDataSeries(@NotNull ProfilerClient client, int id, Common.Session session) {
    this(client, id, session, false);
  }

  public ActivityEventDataSeries(@NotNull ProfilerClient client, int id, Common.Session session, boolean fragmentOnly) {
    myClient = client;
    myProcessId = id;
    mySession = session;
    myFragmentsOnly = fragmentOnly;
  }

  @Override
  public List<SeriesData<EventAction<StackedEventType>>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    List<SeriesData<EventAction<StackedEventType>>> seriesData = new ArrayList<>();
    EventServiceGrpc.EventServiceBlockingStub eventService = myClient.getEventClient();

    // TODO: update getComponentData to accept a fragment filter. There isn't a significant amount of data here,
    // so for the first iteration performance is not a concern.
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setProcessId(myProcessId)
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()));
    EventProfiler.ActivityDataResponse response = eventService.getActivityData(dataRequestBuilder.build());
    for (EventProfiler.ActivityData data : response.getDataList()) {
      long actionStart = 0;
      long actionEnd = 0;
      // If we are listening to only fragments filter non-fragment types.
      // If we only want non-fragment types then filter fragments.
      boolean isFragment = !data.getFragmentData().equals(EventProfiler.FragmentData.getDefaultInstance());
      if (myFragmentsOnly != isFragment) {
        continue;
      }

      boolean haveEvent = false;
      for (int i = 0; i < data.getStateChangesCount(); i++) {
        EventProfiler.ActivityStateData state = data.getStateChanges(i);
        StackedEventType action = StackedEventType.NONE;
        String displayString = data.getName();
        // Match start states with end states.
        switch (state.getState()) {
          case ADDED:
          case RESUMED:
            action = StackedEventType.ACTIVITY_STARTED;
            actionStart = TimeUnit.NANOSECONDS.toMicros(state.getTimestamp());
            break;
          case DESTROYED:
            // This case is a fallthrough to set the action, and end time used in creating the proper UI event.

            // In the UI we track the end of an activity when the activity gets paused.
            // We also listen to the destroyed event here in case the app stops and we force a destroyed event.
            // If we only listen for a destroyed event, the timeline may jump, or never get called depending on
            // how the application is handling references.
            // If we get a destroyed event out of order, and it is the last event we encounter then we know
            // the activity was unexpectedly terminated.
            if (i != data.getStateChangesCount() - 1) {
              break;
            }
            displayString = String.format("%s - %s", displayString, state.getState().toString().toLowerCase());
          case REMOVED:
            // Remove is also a fallthrough as this is the event that gets set when we terminate a fragment.
          case PAUSED:
            action = StackedEventType.ACTIVITY_COMPLETED;
            actionEnd = TimeUnit.NANOSECONDS.toMicros(state.getTimestamp());
            haveEvent = true;
            break;
          default:
            break;
        }
        //Peek at the upcoming stages. If it is resume, started, or create we need to add the current
        //activity range to the results to be displayed in the UI. If it is not one of those, then
        //we want to get the state the activity is currently in to set the name properly.
        while (haveEvent && ++i < data.getStateChangesCount()) {
          state = data.getStateChanges(i);
          EventProfiler.ActivityStateData.ActivityState activityState = state.getState();
          if (getComponentInStartingOrRunningState(activityState)) {
            i--;
            break;
          }
          else {
            displayString = String.format("%s - %s", displayString, state.getState().toString().toLowerCase());
          }
        }

        // We create a UI event each time we match a start and end event, or if we are at the end of our events, and we
        // have a start, or end event. We can have a start event if we scrubbed and the end event is out of range.
        // We can have an end only event if we scrubbed and the start event is out of range.
        if (haveEvent || (i == data.getStateChangesCount() - 1 && action != StackedEventType.NONE)) {
          seriesData.add(new SeriesData<>(actionStart, new ActivityAction(actionStart, actionEnd, action, displayString, data.getHash(),
                                                                          data.getFragmentData().getActivityContextHash())));
          actionEnd = 0;
          actionStart = 0;
          // This is needed as we may have the following scenario,
          // [STARTED, RESUMED, PAUSED, SAVED, STARTED, RESUMED, PAUSED, SAVED].
          // In this state we have 2 UI events for one ActivityData, as such we need to reset our internal state
          // to capture the second event.
          haveEvent = false;
        }
      }
    }

    return seriesData;
  }

  /**
   * This function returns true if the activity is in the "running", or "starting" state. Because there is not a "running" state,
   * the last state transition we receive is RESUMED. If the activity is in any state leading up to and including the RESUME state, then
   * we consider the activity "running", or "starting". This function is used to determine if the set of states we are looking at is
   * in the "stopping", or "teardown" states. We align timings of UI events to the RESUME and PAUSED state as those are the states that
   * the user can directly associate with the visuals, as well as where sometimes users erroneously put initialization logic.
   */
  private boolean getComponentInStartingOrRunningState(EventProfiler.ActivityStateData.ActivityState state) {
    switch (state) {
      case ADDED:
      case ATTACHED:
      case CREATED:
      case CREATEDVIEW:
      case ACTIVITYCREATED:
      case STARTED:
      case RESUMED:
        return true;
      default:
        return false;
    }
  }
}
