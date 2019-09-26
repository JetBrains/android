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


import com.android.tools.adtui.model.trackgroup.TrackGroupListModel;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.EventMonitor;
import org.jetbrains.annotations.NotNull;

public class CustomEventProfilerStage extends Stage {

  private final EventMonitor myEventMonitor;
  private final TrackGroupListModel myTrackGroupListModel = new TrackGroupListModel();

  public CustomEventProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myEventMonitor = new EventMonitor(profilers);
    initTrackGroupList();
  }

  @Override
  public void enter() {
    myEventMonitor.enter();
  }

  @Override
  public void exit() {
    myEventMonitor.exit();
  }

  /**
   * Initializes tracks for all the events that the user records.
   */
  private void initTrackGroupList() {
    myTrackGroupListModel.clear();
    myTrackGroupListModel.addTrackGroupModel(TrackGroupModel.newBuilder().setTitle("Custom Events"));

    //TODO: add the user defined tracks to the custom events list model
  }

  @NotNull
  public TrackGroupListModel getTrackGroupListModel() {
    return myTrackGroupListModel;
  }
}
