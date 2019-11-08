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
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.formatter.UserCounterAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class CustomEventMonitor extends ProfilerMonitor {

  @NotNull private final StateChartModel<Long> myEventModel;
  @NotNull private final CustomEventMonitorLegend myLegend;


  public CustomEventMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);

    UserCounterDataSeries myDataSeries = new UserCounterDataSeries(profilers.getClient().getTransportClient(), profilers);
    myEventModel = createEventChartModel(getTimeline(), myDataSeries);
    myLegend = new CustomEventMonitorLegend(getTimeline().getDataRange(), getTimeline().getViewRange(), myDataSeries);
  }

  @Override
  @NotNull
  public String getName() {
    return "CUSTOM EVENTS";
  }

  @Override
  public TooltipModel buildTooltip() {
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

  @NotNull
  public CustomEventMonitorLegend getLegend() {
    return myLegend;
  }

  private static StateChartModel<Long> createEventChartModel(Timeline timeline, UserCounterDataSeries dataSeries) {
    StateChartModel<Long> stateChartModel = new StateChartModel<>();
    stateChartModel.addSeries(new RangedSeries<>(timeline.getViewRange(), dataSeries, timeline.getDataRange()));
    return stateChartModel;
  }

  /**
   * Legend for Custom Event Monitor that displays whether the event count is: None, Light, Medium, or Heavy.
   */
  public static final class CustomEventMonitorLegend extends LegendComponentModel {

    @NotNull
    private final SeriesLegend myUsageLegend;

    public CustomEventMonitorLegend(@NotNull Range dataRange,
                                    @NotNull Range viewRange,
                                    UserCounterDataSeries dataSeries) {
      super(dataRange);
      RangedContinuousSeries rangedContinuousSeries = new RangedContinuousSeries("", viewRange, new Range(0, 0), dataSeries, dataRange);
      myUsageLegend = new SeriesLegend(rangedContinuousSeries, UserCounterAxisFormatter.DEFAULT, dataRange);

      add(myUsageLegend);
    }

    @NotNull
    public SeriesLegend getUsageLegend() {
      return myUsageLegend;
    }
  }
}

