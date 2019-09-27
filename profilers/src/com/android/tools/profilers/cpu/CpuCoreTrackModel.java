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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Track model for CPU core states in CPU capture stage.
 */
public class CpuCoreTrackModel {
  private final AtraceDataSeries<CpuThreadSliceInfo> myDataSeries;
  private final StateChartModel<CpuThreadSliceInfo> myStateChartModel;
  private final int myCpuId;
  private final int myAppProcessId;

  public CpuCoreTrackModel(@NotNull Range range, @NotNull AtraceCpuCapture atraceCapture, int cpuId, int appProcessId) {
    myDataSeries = new AtraceDataSeries<>(atraceCapture, capture -> capture.getCpuThreadSliceInfoStates(cpuId));
    myStateChartModel = new StateChartModel<>();
    myStateChartModel.addSeries(new RangedSeries<>(range, myDataSeries));
    myCpuId = cpuId;
    myAppProcessId = appProcessId;
  }

  public AtraceDataSeries<CpuThreadSliceInfo> getDataSeries() {
    return myDataSeries;
  }

  public StateChartModel<CpuThreadSliceInfo> getStateChartModel() {
    return myStateChartModel;
  }

  public int getCpuId() {
    return myCpuId;
  }

  public int getAppProcessId() {
    return myAppProcessId;
  }
}
