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

import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import org.jetbrains.annotations.NotNull;

/**
 * Track model for the Custom Event Visualization.
 */
public class CustomEventTrackModel {
  @NotNull private final UserCounterModel myLineChartModel;
  @NotNull private final AxisComponentModel myAxisComponentModel;

  public CustomEventTrackModel(UserCounterModel userCounterModel) {
    myLineChartModel = userCounterModel;

    // Resizable axis that will adjust to the range of the user's event.
    myAxisComponentModel =
      new ResizingAxisComponentModel.Builder(myLineChartModel.getUsageRange(), new SingleUnitAxisFormatter(1, 5, 5, ""))
        .build();

    // TODO: add tooltip (b/139199653) and legend model (b/141710789)
  }

  @NotNull
  public LineChartModel getLineChartModel() { return myLineChartModel; }

  @NotNull
  public AxisComponentModel getAxisComponentModel() {
    return myAxisComponentModel;
  }
}
