/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.common.EnumColors;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.nodemodel.AtraceNodeModel;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.profilers.cpu.capturedetails.CaptureNodeHRenderer.toUnmatchColor;

/**
 * Defines the colors (fill and border) of the rectangles used to represent {@link AtraceNodeModel} nodes in a
 * {@link com.android.tools.adtui.chart.hchart.HTreeChart}.
 */
class AtraceNodeModelHChartColors {

  private static EnumColors<CpuProfilerStage.ThreadState> threadColors = ProfilerColors.THREAD_STATES.build();

  private static void validateModel(@NotNull CaptureNodeModel model) {
    if (!(model instanceof AtraceNodeModel)) {
      throw new IllegalStateException("Model must be an instance of AtraceNodeModel.");
    }
  }

  /**
   * For cpu idle time (time the node is scheduled wall clock, but not thread time), we match thread colors.
   */
  static Color getIdleCpuColor(@NotNull CaptureNodeModel model,
                               CaptureModel.Details.Type chartType,
                               boolean isUnmatched,
                               boolean isFocused) {
    Color color;
    if (chartType == CaptureModel.Details.Type.CALL_CHART) {
      threadColors.setColorIndex(isFocused ? 1 : 0);
      color = threadColors.getColor(CpuProfilerStage.ThreadState.RUNNABLE_CAPTURED);
    }
    else {
      // Atrace captures do not know where calls come from so we always use APP.
      color = isFocused ? ProfilerColors.CPU_FLAMECHART_APP_HOVER : ProfilerColors.CPU_FLAMECHART_APP;
    }
    return isUnmatched ? toUnmatchColor(color) : color;
  }

  /**
   * We use the usage captured color. This gives the UI a consistent look
   * across CPU, Kernel, Threads, and trace nodes.
   */
  static Color getFillColor(@NotNull CaptureNodeModel model, CaptureModel.Details.Type chartType, boolean isUnmatched, boolean isFocused) {
    validateModel(model);
    Color color;
    if (chartType == CaptureModel.Details.Type.CALL_CHART) {
      threadColors.setColorIndex(isFocused ? 1 : 0);
      color = threadColors.getColor(CpuProfilerStage.ThreadState.RUNNING_CAPTURED);
    }
    else {
      // Atrace captures do not know where calls come from so we always use APP.
      color = isFocused ? ProfilerColors.CPU_FLAMECHART_APP_HOVER : ProfilerColors.CPU_FLAMECHART_APP;
    }
    return isUnmatched ? toUnmatchColor(color) : color;
  }
}
