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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.ProfilerTooltip;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class CustomEventMonitor extends ProfilerMonitor {

  @NotNull private final StateChartModel<Long> myEventModel;

  public CustomEventMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);

    myEventModel = createEventChartModel(profilers);
  }

  @Override
  @NotNull
  public String getName() {
    return "CUSTOM EVENTS";
  }

  @Override
  public ProfilerTooltip buildTooltip() {
    return new CustomEventMonitorTooltip(this);
  }

  @Override
  public void exit() {
  }

  @Override
  public void enter() {
  }

  @Override
  public void expand() {
    myProfilers.setStage(new CustomEventProfilerStage(myProfilers));
  }

  @NotNull
  public StateChartModel<Long> getEventModel() {
    return myEventModel;
  }

  private static StateChartModel<Long> createEventChartModel(StudioProfilers profilers) {
    StateChartModel<Long> stateChartModel = new StateChartModel<>();
    Range range = profilers.getTimeline().getViewRange();
    Range dataRange = profilers.getTimeline().getDataRange();
    TransportServiceGrpc.TransportServiceBlockingStub transportClient = profilers.getClient().getTransportClient();

    stateChartModel.addSeries(new RangedSeries<>(range, new UserCounterDataSeries(transportClient, profilers), dataRange));

    return stateChartModel;
  }
}

