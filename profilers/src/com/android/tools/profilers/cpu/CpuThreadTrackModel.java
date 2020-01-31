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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import org.jetbrains.annotations.NotNull;

/**
 * Track model for CPU threads in CPU capture stage. Consists of thread states and trace events.
 */
public class CpuThreadTrackModel {
  private final DataSeries<CpuProfilerStage.ThreadState> myThreadStateDataSeries;
  private final StateChartModel<CpuProfilerStage.ThreadState> myThreadStateChartModel;
  private final CaptureDetails.CallChart myCallChartModel;
  private final Range myCaptureRange;

  public CpuThreadTrackModel(@NotNull StudioProfilers profilers, @NotNull Range range, @NotNull CpuCapture capture, int threadId) {
    myThreadStateDataSeries = new CpuThreadStateDataSeries(profilers.getClient().getTransportClient(),
                                                           profilers.getSession().getStreamId(),
                                                           profilers.getSession().getPid(),
                                                           threadId,
                                                           capture);
    myThreadStateChartModel = new StateChartModel<>();
    myThreadStateChartModel.addSeries(new RangedSeries<>(range, myThreadStateDataSeries));

    myCallChartModel = new CaptureDetails.CallChart(range, capture.getCaptureNode(threadId), capture);
    myCaptureRange = capture.getRange();
  }

  @NotNull
  public DataSeries<CpuProfilerStage.ThreadState> getThreadStateDataSeries() {
    return myThreadStateDataSeries;
  }

  @NotNull
  public StateChartModel<CpuProfilerStage.ThreadState> getThreadStateChartModel() {
    return myThreadStateChartModel;
  }

  @NotNull
  public CaptureDetails.CallChart getCallChartModel() {
    return myCallChartModel;
  }

  @NotNull
  public Range getCaptureRange() {
    return myCaptureRange;
  }
}
