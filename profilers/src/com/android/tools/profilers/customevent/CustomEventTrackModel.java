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

import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import org.jetbrains.annotations.NotNull;

/**
 * Track model for the Custom Event Visualization.
 */
public class CustomEventTrackModel {
  @NotNull private final UserCounterModel myLineChartModel;
  @NotNull private final AxisComponentModel myAxisComponentModel;
  @NotNull private final Legends myLegends;

  public CustomEventTrackModel(UserCounterModel userCounterModel, Range dataRange) {
    myLineChartModel = userCounterModel;

    // Resizable axis that will adjust to the range of the user's event.
    myAxisComponentModel =
      new ResizingAxisComponentModel.Builder(myLineChartModel.getUsageRange(), new SingleUnitAxisFormatter(1, 5, 5, ""))
        .build();

    myLegends = new Legends(myLineChartModel, dataRange);

    // TODO: add tooltip (b/139199653) model
  }

  @NotNull
  public LineChartModel getLineChartModel() { return myLineChartModel; }

  @NotNull
  public AxisComponentModel getAxisComponentModel() {
    return myAxisComponentModel;
  }

  @NotNull
  public CustomEventTrackModel.Legends getLegends() {
    return myLegends;
  }

  @NotNull
  public String getName() {
    return myLineChartModel.getEventName();
  }

  public static class Legends extends LegendComponentModel {

    @NotNull
    private final SeriesLegend myTrackLegend;

    public Legends(@NotNull LineChartModel usage, @NotNull Range range) {
      super(range);
      myTrackLegend =
        new SeriesLegend(usage.getSeries().get(0), new SingleUnitAxisFormatter(1, 5, 10, ""), range, Interpolatable.SegmentInterpolator);
      add(myTrackLegend);
    }

    @NotNull
    public SeriesLegend getTrackLegend() {
      return myTrackLegend;
    }
  }
}
