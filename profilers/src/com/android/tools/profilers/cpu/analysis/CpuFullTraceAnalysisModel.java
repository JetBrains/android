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
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import org.jetbrains.annotations.NotNull;

public class CpuFullTraceAnalysisModel extends CpuAnalysisModel {
  private static final String DEFAULT_ANALYSIS_NAME = "Full trace";
  public CpuFullTraceAnalysisModel(@NotNull CpuCapture capture, @NotNull Range selectionRange) {
    super(DEFAULT_ANALYSIS_NAME);
    init(capture, selectionRange);
  }

  private void init(@NotNull CpuCapture capture, @NotNull Range selectionRange) {
    getTabs().add(new CpuAnalysisTabModel<>(SUMMARY_TITLE));
    // Flame Chart
    CpuAnalysisChartModel
      flameModel = new CpuAnalysisChartModel(FLAME_CHART_TITLE, CaptureDetails.Type.FLAME_CHART, selectionRange, capture);
    getTabs().add(flameModel);

    // Top Down
    CpuAnalysisChartModel
      topDown = new CpuAnalysisChartModel(TOP_DOWN_TITLE, CaptureDetails.Type.TOP_DOWN, selectionRange, capture);
    getTabs().add(topDown);

    // Bottom up
    CpuAnalysisChartModel
      bottomUp = new CpuAnalysisChartModel(BOTTOM_UP_TITLE, CaptureDetails.Type.BOTTOM_UP, selectionRange, capture);
    getTabs().add(bottomUp);
    capture.getCaptureNodes().forEach(node -> {
      flameModel.addData(node);
      topDown.addData(node);
      bottomUp.addData(node);
    });
  }
}
