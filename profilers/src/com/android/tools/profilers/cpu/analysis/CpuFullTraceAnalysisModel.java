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
package com.android.tools.profilers.cpu.analysis;

import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.cpu.CpuCapture;
import org.jetbrains.annotations.NotNull;

public class CpuFullTraceAnalysisModel extends CpuAnalysisModel<CpuCapture> {
  private static final String DEFAULT_ANALYSIS_NAME = "All threads";

  public CpuFullTraceAnalysisModel(@NotNull CpuCapture capture, @NotNull Range selectionRange) {
    super(DEFAULT_ANALYSIS_NAME);
    init(capture, selectionRange);
  }

  private void init(@NotNull CpuCapture capture, @NotNull Range selectionRange) {
    // Flame Chart
    CpuAnalysisChartModel<CpuCapture> flameModel =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.FLAME_CHART, selectionRange, capture, CpuCapture::getCaptureNodes);
    flameModel.getDataSeries().add(capture);
    addTabModel(flameModel);

    // Top Down
    CpuAnalysisChartModel<CpuCapture> topDown =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.TOP_DOWN, selectionRange, capture, CpuCapture::getCaptureNodes);
    topDown.getDataSeries().add(capture);
    addTabModel(topDown);

    // Bottom up
    CpuAnalysisChartModel<CpuCapture> bottomUp =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.BOTTOM_UP, selectionRange, capture, CpuCapture::getCaptureNodes);
    bottomUp.getDataSeries().add(capture);
    addTabModel(bottomUp);
  }
}
