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

import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.EventMonitor;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class CustomEventProfilerStage extends Stage {

  private final EventMonitor myEventMonitor;
  private final List<TrackGroupModel> myTrackGroupModels = new ArrayList<>();
  //TODO (b/139200794) Get a list of the registered events from the user
  @NotNull private final List<String> myEventNames = new ArrayList<>();
  @NotNull private final List<UserCounterModel> myUserCounterModels = new ArrayList<>();

  public CustomEventProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myEventMonitor = new EventMonitor(profilers);
  }

  @Override
  public void enter() {
    myEventMonitor.enter();
    initTrackGroupList();
  }

  @Override
  public void exit() {
    // Unregister all of the user counter models so that the updater does not hold a reference to them when the stage is deleted.
    for (UserCounterModel model : myUserCounterModels) {
      getStudioProfilers().getUpdater().unregister(model);
    }

    myEventMonitor.exit();
  }

  /**
   * Initializes tracks for all the events that the user records.
   */
  private void initTrackGroupList() {
    myTrackGroupModels.clear();
    TrackGroupModel eventTrackGroupModel = TrackGroupModel.newBuilder().setTitle("Custom Events").build();
    myTrackGroupModels.add(eventTrackGroupModel);

    for (String eventName : myEventNames) {
      // Create a data model for the specific event name and register it.
      UserCounterModel dataModel = new UserCounterModel(getStudioProfilers(), eventName);
      getStudioProfilers().getUpdater().register(dataModel);
      myUserCounterModels.add(dataModel);

      // Create a track model with that has the data model for this event.
      CustomEventTrackModel customEventTrackModel = new CustomEventTrackModel(dataModel);

      eventTrackGroupModel.addTrackModel(
        TrackModel.newBuilder(customEventTrackModel,
                         ProfilerTrackRendererType.CUSTOM_EVENTS,
                         eventName));
    }
  }

  @NotNull
  public List<TrackGroupModel> getTrackGroupModels() {
    return myTrackGroupModels;
  }

  @NotNull
  @VisibleForTesting
  List<String> getEventNames() {
    return myEventNames;
  }
}