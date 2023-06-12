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
package com.android.tools.profilers.customevent;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.LifecycleEventModel;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.StreamingStage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.LifecycleEventDataSeries;
import com.android.tools.profilers.event.UserEventDataSeries;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class CustomEventProfilerStage extends StreamingStage {

  private final List<TrackGroupModel> myTrackGroupModels = new ArrayList<>();
  @NotNull private final List<UserCounterModel> myUserCounterModels = new ArrayList<>();
  @NotNull private final AspectObserver myAspectObserver = new AspectObserver();
  @NotNull private final UserCounterAspectModel myUserCounterAspectModel = new UserCounterAspectModel();
  @NotNull private final TrackGroupModel myEventTrackGroupModel;


  public CustomEventProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myEventTrackGroupModel = TrackGroupModel.newBuilder().setTitle("Custom Events").build();

    // Checks to see if a new event has been added
    getTimeline().getViewRange().addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::updateEventNames);
    updateEventNames();
  }

  @Override
  public void enter() {
    logEnterStage();
    initTrackGroupList();
  }

  @Override
  public void exit() {
    // Unregister all of the user counter models so that the updater does not hold a reference to them when the stage is deleted.
    for (UserCounterModel model : myUserCounterModels) {
      getStudioProfilers().getUpdater().unregister(model);
    }
  }

  @Override
  public AndroidProfilerEvent.Stage getStageType() {
    return AndroidProfilerEvent.Stage.UNKNOWN_STAGE;
  }

  /**
   * Initializes tracks for all the events that the user records.
   */
  private void initTrackGroupList() {
    myTrackGroupModels.clear();
    myTrackGroupModels.add(createInteractionTrackGroup());
    myTrackGroupModels.add(myEventTrackGroupModel);

    for (String eventName : myUserCounterAspectModel.getEventNames()) {
      // Add events to the Custom Events Track Group
      myEventTrackGroupModel.addTrackModel(
        TrackModel.newBuilder(createTrackModel(eventName),
                              ProfilerTrackRendererType.CUSTOM_EVENTS,
                              eventName).setHideHeader(true));
    }
  }

  /**
   * Returns a track model containing a UserCounterModel for the given event name.
   */
  private CustomEventTrackModel createTrackModel(String eventName) {
    // Create a data model for the specific event name and register it.
    UserCounterModel dataModel = new UserCounterModel(getStudioProfilers(), eventName);
    getStudioProfilers().getUpdater().register(dataModel);
    myUserCounterModels.add(dataModel);

    // Create a track model with that has the data model for this event.
    Range dataRange = getTimeline().getDataRange();
    return new CustomEventTrackModel(dataModel, dataRange);
  }


  private TrackGroupModel createInteractionTrackGroup() {
    Range viewRange = getTimeline().getViewRange();
    TrackGroupModel interaction = TrackGroupModel.newBuilder().setTitle("Interaction").build();
    interaction.addTrackModel(
      TrackModel.newBuilder(
        new EventModel<>(new RangedSeries<>(viewRange, new UserEventDataSeries(getStudioProfilers()))),
        ProfilerTrackRendererType.USER_INTERACTION,
        "User"));
    interaction.addTrackModel(
      TrackModel.newBuilder(
        new LifecycleEventModel(
          new RangedSeries<>(viewRange, new LifecycleEventDataSeries(getStudioProfilers(), false)),
          new RangedSeries<>(viewRange, new LifecycleEventDataSeries(getStudioProfilers(), true))),
        ProfilerTrackRendererType.APP_LIFECYCLE,
        "Lifecycle"));
    return interaction;
  }

  @NotNull
  public List<TrackGroupModel> getTrackGroupModels() {
    return myTrackGroupModels;
  }

  /**
   * Checks to see if a new event has been added that has not been seen before. Adds a track for any new events found.
   */
  @VisibleForTesting
  void updateEventNames() {
    Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(getStudioProfilers().getSession().getStreamId())
      .setPid(getStudioProfilers().getSession().getPid())
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .build();

    Transport.GetEventGroupsResponse response = getStudioProfilers().getClient().getTransportClient().getEventGroups(request);

    if (myTrackGroupModels.isEmpty()) {
      // If the track group model has been cleared, need to reload tracks first.
      initTrackGroupList();
    }

    // Check to see if another event has been added that has not already been seen
    if (response.getGroupsCount() > myUserCounterAspectModel.getSize()) {
      for (Transport.EventGroup eventGroup : response.getGroupsList()) {
        if (eventGroup.getEventsCount() > 0) {

          // Event names are used as the identifier for unique events
          String eventName = eventGroup.getEventsList().get(0).getUserCounters().getName();

          // Take the event that has not been seen and add it to the User Counters set
          // add() method will return true if the event name has not been seen before
          if (myUserCounterAspectModel.add(eventName)) {
            myEventTrackGroupModel.addTrackModel(
              TrackModel.newBuilder(createTrackModel(eventName),
                                    ProfilerTrackRendererType.CUSTOM_EVENTS,
                                    eventName).setHideHeader(true));
          }
        }
      }
    }
  }

  @NotNull
  public UserCounterAspectModel getUserCounterAspectModel() {
    return myUserCounterAspectModel;
  }
}