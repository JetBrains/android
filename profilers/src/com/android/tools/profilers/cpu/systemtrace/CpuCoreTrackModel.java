/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import org.jetbrains.annotations.NotNull;

/**
 * Track model for CPU core states in CPU capture stage.
 */
public class CpuCoreTrackModel {
  private final StateChartModel<CpuThreadSliceInfo> myStateChartModel;
  private final int myAppProcessId;

  public CpuCoreTrackModel(@NotNull DataSeries<CpuThreadSliceInfo> dataSeries,
                           @NotNull Range range,
                           int mainThreadId) {
    myStateChartModel = new StateChartModel<>();
    myStateChartModel.addSeries(new RangedSeries<>(range, dataSeries));
    myAppProcessId = mainThreadId;
  }

  public StateChartModel<CpuThreadSliceInfo> getStateChartModel() {
    return myStateChartModel;
  }

  public int getAppProcessId() {
    return myAppProcessId;
  }
}
