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

import static com.android.tools.profilers.cpu.capturedetails.CaptureNodeHRenderer.toUnmatchColor;

import com.android.tools.profilers.DataVisualizationColors;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import java.awt.Color;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the fill color of the rectangles used to represent {@link SingleNameModel} nodes in a
 * {@link com.android.tools.adtui.chart.hchart.HTreeChart}.
 */
final class SingleNameModelHChartColors {

  private static void validateModel(@NotNull CaptureNodeModel model) {
    if (!(model instanceof SingleNameModel)) {
      throw new IllegalStateException("Model must be an instance of SingleNameModel.");
    }
  }

  static Color getFillColor(@NotNull CaptureNodeModel model,
                            CaptureDetails.Type chartType,
                            boolean isUnmatched,
                            boolean isFocused,
                            boolean isDeselected) {
    validateModel(model);

    Color color;
    if (chartType == CaptureDetails.Type.CALL_CHART) {
      if (isDeselected) {
        color = DataVisualizationColors.getPaletteManager().getBackgroundColor(
          DataVisualizationColors.BACKGROUND_DATA_COLOR_NAME, isFocused);
      }
      else {
        color = isFocused ? ProfilerColors.CPU_CALLCHART_APP_HOVER : ProfilerColors.CPU_CALLCHART_APP;
      }
    }
    else {
      color = isFocused ? ProfilerColors.CPU_FLAMECHART_APP_HOVER : ProfilerColors.CPU_FLAMECHART_APP;
    }
    return isUnmatched ? toUnmatchColor(color) : color;
  }
}
