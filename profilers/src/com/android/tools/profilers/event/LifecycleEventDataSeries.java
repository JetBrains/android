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
import com.android.tools.adtui.model.event.LifecycleAction;
import com.android.tools.adtui.model.event.LifecycleEvent;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profiler.proto.Interaction;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.util.text.StringUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public class LifecycleEventDataSeries implements DataSeries<EventAction<LifecycleEvent>> {

  @NotNull private StudioProfilers myProfilers;
  @NotNull private final Common.Session mySession;
  private final boolean myFragmentsOnly;

  public LifecycleEventDataSeries(@NotNull StudioProfilers profilers, boolean fragmentOnly) {
    myProfilers = profilers;
    mySession = profilers.getSession();
    myFragmentsOnly = fragmentOnly;
  }

  @Override
  public List<SeriesData<EventAction<LifecycleEvent>>> getDataForRange(@NotNull Range timeCurrentRangeUs) {
    if (myProfilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      return getTransportData(timeCurrentRangeUs);
    }
    else {
      return getLegacyData(timeCurrentRangeUs);
    }
  }

  @NotNull
  private List<SeriesData<EventAction<LifecycleEvent>>> getTransportData(@NotNull Range rangeUs) {
    List<SeriesData<EventAction<LifecycleEvent>>> series = new ArrayList<>();
    Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
      .setKind(Common.Event.Kind.VIEW)
      .setStreamId(mySession.getStreamId())
      .setPid(mySession.getPid())
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin()))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax()))
      .build();
    Transport.GetEventGroupsResponse response = myProfilers.getClient().getTransportClient().getEventGroups(request);
    for (Transport.EventGroup group : response.getGroupsList()) {
      boolean isFragment = group.getEvents(0).getView().getParentActivityId() != 0;
      // If we are listening to only fragments filter non-fragment types.
      // If we only want non-fragment types then filter fragments.
      if (myFragmentsOnly != isFragment) {
        continue;
      }

      long actionStart = 0;
      long actionEnd = 0;
      boolean hasEndEvent = false;
      LifecycleEvent lifecycleEvent = LifecycleEvent.NONE;
      for (int i = 0; i < group.getEventsCount(); i++) {
        Common.Event event = group.getEvents(i);
        Interaction.ViewData data = event.getView();
        String displayString = data.getName();

        // Match start states with end states.
        switch (data.getState()) {
          case ADDED:
          case RESUMED:
            lifecycleEvent = LifecycleEvent.STARTED;
            actionStart = TimeUnit.NANOSECONDS.toMicros(event.getTimestamp());
            break;
          case DESTROYED:
            // This case is a fallthrough to set the lifecycle, and end time used in creating the proper UI event.

            // In the UI we track the end of an activity when the activity gets paused.
            // We also listen to the destroyed event here in case the app stops and we force a destroyed event.
            // If we only listen for a destroyed event, the timeline may jump, or never get called depending on
            // how the application is handling references.
            // If we get a destroyed event out of order, and it is the last event we encounter then we know
            // the activity was unexpectedly terminated.
            if (i != group.getEventsCount() - 1) {
              break;
            }
            displayString += String.format(" - %s", data.getState().toString().toLowerCase());
            // Falls-through to REMOVED
          case REMOVED:
            // Remove is also a fallthrough as this is the event that gets set when we terminate a fragment.
          case PAUSED:
            lifecycleEvent = LifecycleEvent.COMPLETED;
            actionEnd = TimeUnit.NANOSECONDS.toMicros(event.getTimestamp());
            hasEndEvent = true;
            break;
          default:
            break;
        }

        // Add all the states of the activity to the name until we have reached the end or the next start event.
        if (hasEndEvent) {
          while (++i < group.getEventsCount()) {
            Common.Event nextEvent = group.getEvents(i);
            if (nextEvent.getIsEnded()) {
              displayString += String.format(" - %s", nextEvent.getView().getState().toString().toLowerCase());
            }
            else {
              i--;
              break;
            }
          }
        }

        // We create a UI event each time we have an end event, or if there is a start event and we have reached the end of the group.
        if (hasEndEvent || (i == group.getEventsCount() - 1 && actionStart != 0)) {
          series.add(new SeriesData<>(actionStart,
                                      new LifecycleAction(actionStart, actionEnd, lifecycleEvent, displayString, event.getGroupId())));

          // Reset the states for the next set of states that make up a SeriesData.
          actionStart = 0;
          actionEnd = 0;
          hasEndEvent = false;
          lifecycleEvent = LifecycleEvent.NONE;
        }
      }
    }

    Collections.sort(series, Comparator.comparingLong(data -> data.x));
    return series;
  }

  @NotNull
  private List<SeriesData<EventAction<LifecycleEvent>>> getLegacyData(@NotNull Range rangeUs) {
    List<SeriesData<EventAction<LifecycleEvent>>> seriesData = new ArrayList<>();
    EventServiceGrpc.EventServiceBlockingStub eventService = myProfilers.getClient().getEventClient();

    // TODO: update getComponentData to accept a fragment filter. There isn't a significant amount of data here,
    // so for the first iteration performance is not a concern.
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax()));
    EventProfiler.ActivityDataResponse response = eventService.getActivityData(dataRequestBuilder.build());
    for (EventProfiler.ActivityData data : response.getDataList()) {
      long actionStart = 0;
      long actionEnd = 0;
      // If we are listening to only fragments filter non-fragment types.
      // If we only want non-fragment types then filter fragments.
      boolean isFragment = data.getActivityContextHash() != 0;
      if (myFragmentsOnly != isFragment) {
        continue;
      }

      boolean haveEvent = false;
      for (int i = 0; i < data.getStateChangesCount(); i++) {
        EventProfiler.ActivityStateData state = data.getStateChanges(i);
        LifecycleEvent lifecycleEvent = LifecycleEvent.NONE;
        String displayString = data.getName();
        // Match start states with end states.
        switch (state.getState()) {
          case ADDED:
          case RESUMED:
            lifecycleEvent = LifecycleEvent.STARTED;
            actionStart = TimeUnit.NANOSECONDS.toMicros(state.getTimestamp());
            break;
          case DESTROYED:
            // This case is a fallthrough to set the lifecycle, and end time used in creating the proper UI event.

            // In the UI we track the end of an activity when the activity gets paused.
            // We also listen to the destroyed event here in case the app stops and we force a destroyed event.
            // If we only listen for a destroyed event, the timeline may jump, or never get called depending on
            // how the application is handling references.
            // If we get a destroyed event out of order, and it is the last event we encounter then we know
            // the activity was unexpectedly terminated.
            if (i != data.getStateChangesCount() - 1) {
              break;
            }
            displayString = String.format("%s - %s", displayString, StringUtil.toLowerCase(state.getState().toString()));
            // Falls-through to REMOVED
          case REMOVED:
            // Remove is also a fallthrough as this is the event that gets set when we terminate a fragment.
          case PAUSED:
            lifecycleEvent = LifecycleEvent.COMPLETED;
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
          Interaction.ViewData.State activityState = state.getState();
          if (getComponentInStartingOrRunningState(activityState)) {
            i--;
            break;
          }
          else {
            displayString = String.format("%s - %s", displayString, StringUtil.toLowerCase(state.getState().toString()));
          }
        }

        // We create a UI event each time we match a start and end event, or if we are at the end of our events, and we
        // have a start, or end event. We can have a start event if we scrubbed and the end event is out of range.
        // We can have an end only event if we scrubbed and the start event is out of range.
        if (haveEvent || (i == data.getStateChangesCount() - 1 && lifecycleEvent != LifecycleEvent.NONE)) {
          seriesData
            .add(new SeriesData<>(actionStart, new LifecycleAction(actionStart, actionEnd, lifecycleEvent, displayString, data.getHash())));
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
  private boolean getComponentInStartingOrRunningState(Interaction.ViewData.State state) {
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
