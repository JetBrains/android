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

package com.android.tools.profilers.cpu;

import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.nodemodel.AtraceNodeModel;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.profilers.cpu.CaptureNodeHRenderer.toUnmatchColor;

/**
 * Defines the colors (fill and border) of the rectangles used to represent {@link AtraceNodeModel} nodes in a
 * {@link com.android.tools.adtui.chart.hchart.HTreeChart}.
 */
class AtraceNodeModelHChartColors {

  private static void validateModel(@NotNull CaptureNodeModel model) {
    if (!(model instanceof AtraceNodeModel)) {
      throw new IllegalStateException("Model must be an instance of AtraceNodeModel.");
    }
  }

  /**
   * For cpu idle time (time the node is scheduled wall clock, but not thread time). we apply a slightly darker
   * shade of the CPU_USAGE_CAPTURED color to show a subtle difference in timings.
   */
  static Color getIdleCpuColor(@NotNull CaptureNodeModel model, boolean isUnmatched) {
    Color color = ColorUtil.darker(ProfilerColors.CPU_USAGE_CAPTURED, 3);
    return isUnmatched ? toUnmatchColor(color) : color;
  }

  /**
   * We use the usage captured color. This gives the UI a consistent look
   * across CPU, Kernel, Threads, and trace nodes.
   */
  static Color getFillColor(@NotNull CaptureNodeModel model, boolean isUnmatched) {
    validateModel(model);
    Color color = ProfilerColors.CPU_USAGE_CAPTURED;
    return isUnmatched ? toUnmatchColor(color) : color;
  }
}
