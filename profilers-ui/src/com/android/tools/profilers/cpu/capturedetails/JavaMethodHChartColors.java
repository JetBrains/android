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
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import java.awt.Color;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the fill color of the rectangles used to represent {@link JavaMethodModel} nodes in a
 * {@link com.android.tools.adtui.chart.hchart.HTreeChart}.
 */
final class JavaMethodHChartColors {

  private static void validateModel(@NotNull CaptureNodeModel model) {
    if (!(model instanceof JavaMethodModel)) {
      throw new IllegalStateException("Model must be an instance of JavaMethodModel.");
    }
  }

  private static boolean isMethodVendor(CaptureNodeModel method) {
    return method.getFullName().startsWith("java.") ||
           method.getFullName().startsWith("sun.") ||
           method.getFullName().startsWith("javax.") ||
           method.getFullName().startsWith("apple.") ||
           method.getFullName().startsWith("com.apple.");
  }

  private static boolean isMethodPlatform(CaptureNodeModel method) {
    return method.getFullName().startsWith("android.") || method.getFullName().startsWith("com.android.");
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
      else if (isMethodVendor(model)) {
        color = isFocused ? ProfilerColors.CPU_CALLCHART_VENDOR_HOVER : ProfilerColors.CPU_CALLCHART_VENDOR;
      }
      else if (isMethodPlatform(model)) {
        color = isFocused ? ProfilerColors.CPU_CALLCHART_PLATFORM_HOVER : ProfilerColors.CPU_CALLCHART_PLATFORM;
      }
      else {
        color = isFocused ? ProfilerColors.CPU_CALLCHART_APP_HOVER : ProfilerColors.CPU_CALLCHART_APP;
      }
    }
    else {
      if (isMethodVendor(model)) {
        color = isFocused ? ProfilerColors.CPU_FLAMECHART_VENDOR_HOVER : ProfilerColors.CPU_FLAMECHART_VENDOR;
      }
      else if (isMethodPlatform(model)) {
        color = isFocused ? ProfilerColors.CPU_FLAMECHART_PLATFORM_HOVER : ProfilerColors.CPU_FLAMECHART_PLATFORM;
      }
      else {
        color = isFocused ? ProfilerColors.CPU_FLAMECHART_APP_HOVER : ProfilerColors.CPU_FLAMECHART_APP;
      }
    }
    return isUnmatched ? toUnmatchColor(color) : color;
  }
}
