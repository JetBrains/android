/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CpuCaptureView {
  @NotNull
  private final CpuCapture myCapture;

  private final HTreeChart<MethodModel> myCaptureTreeChart;

  public CpuCaptureView(@NotNull CpuCapture capture, @NotNull StudioProfilers profilers) {
    myCapture = capture;

    myCapture.addDependency()
        .setExecutor(ApplicationManager.getApplication()::invokeLater)
        .onChange(CpuCaptureAspect.CAPTURE_THREAD, this::updateThread);

    myCaptureTreeChart = new HTreeChart<>();
    myCaptureTreeChart.setHRenderer(new SampledMethodUsageHRenderer());
    myCaptureTreeChart.setXRange(profilers.getTimeline().getSelectionRange());
  }

  private void updateThread() {
    // Updates the tree displayed in capture panel
    myCaptureTreeChart.setHTree(myCapture.getCaptureNode());
  }

  public JComponent getComponent() {
    return myCaptureTreeChart;
  }

  @NotNull
  public CpuCapture getCapture() {
    return myCapture;
  }

  public void register(Choreographer choreographer) {
    choreographer.register(myCaptureTreeChart);
  }

  public void unregister(Choreographer choreographer) {
    choreographer.unregister(myCaptureTreeChart);
  }
}
