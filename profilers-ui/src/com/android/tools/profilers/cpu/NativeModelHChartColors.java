// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.cpu;


import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.NativeNodeModel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.profilers.cpu.MethodModelHRenderer.toUnmatchColor;

/**
 *  Defines the colors (fill and border) of the rectangles used to represent {@link NativeNodeModel} nodes in a
 *  {@link com.android.tools.adtui.chart.hchart.HTreeChart}.
 */
public class NativeModelHChartColors {

  private static void validateModel(@NotNull CaptureNodeModel model) {
    if (!(model instanceof NativeNodeModel)) {
      throw new IllegalStateException("Model must be a subclass of NativeNodeModel.");
    }
  }

  private static boolean isMethodVendor(CaptureNodeModel method) {
    return method.getFullName().startsWith("openjdkjvmti::");
  }

  private static boolean isMethodPlatform(CaptureNodeModel method) {
    // TODO: include all the art-related methods (e.g. artQuickToInterpreterBridge and artMterpAsmInstructionStart)
    return method.getFullName().startsWith("art::") ||
           method.getFullName().startsWith("android::") ||
           method.getFullName().startsWith("art_") ||
           method.getFullName().startsWith("dalvik-jit-code-cache");
  }

  static Color getFillColor(@NotNull CaptureNodeModel model, CaptureModel.Details.Type chartType, boolean isUnmatched) {
    validateModel(model);

    // TODO(b/68014311): Define colors for each type of model and differentiate user code properly
    Color color;
    if (chartType == CaptureModel.Details.Type.CALL_CHART) {
      if (isMethodVendor(model)) {
        color = ProfilerColors.CPU_CALLCHART_VENDOR;
      }
      else if (isMethodPlatform(model)) {
        color = ProfilerColors.CPU_CALLCHART_PLATFORM;
      }
      else {
        color = ProfilerColors.CPU_CALLCHART_APP;
      }
    }
    else {
      if (isMethodVendor(model)) {
        color = ProfilerColors.CPU_FLAMECHART_VENDOR;
      }
      else if (isMethodPlatform(model)) {
        color = ProfilerColors.CPU_FLAMECHART_PLATFORM;
      }
      else {
        color = ProfilerColors.CPU_FLAMECHART_APP;
      }
    }
    return isUnmatched ? toUnmatchColor(color) : color;
  }

  static Color getBorderColor(@NotNull CaptureNodeModel model, CaptureModel.Details.Type chartType, boolean isUnmatched) {
    validateModel(model);

    // TODO(b/68014311): Define colors for each type of model and differentiate user code properly
    Color color;
    if (chartType == CaptureModel.Details.Type.CALL_CHART) {
      if (isMethodVendor(model)) {
        color = ProfilerColors.CPU_CALLCHART_VENDOR_BORDER;
      }
      else if (isMethodPlatform(model)) {
        color = ProfilerColors.CPU_CALLCHART_PLATFORM_BORDER;
      }
      else {
        color = ProfilerColors.CPU_CALLCHART_APP_BORDER;
      }
    }
    else {
      if (isMethodVendor(model)) {
        color = ProfilerColors.CPU_FLAMECHART_VENDOR_BORDER;
      }
      else if (isMethodPlatform(model)) {
        color = ProfilerColors.CPU_FLAMECHART_PLATFORM_BORDER;
      }
      else {
        color = ProfilerColors.CPU_FLAMECHART_APP_BORDER;
      }
    }
    return isUnmatched ? toUnmatchColor(color) : color;
  }
}
