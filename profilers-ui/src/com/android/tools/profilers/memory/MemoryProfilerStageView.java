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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.profilers.StageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MemoryProfilerStageView extends StageView {
  private static final int CHOREOGRAPHER_FPS = 60;

  @NotNull
  private final Choreographer myChoreographer;

  @NotNull
  private final JComponent myComponent;

  public MemoryProfilerStageView(@NotNull MemoryProfilerStage stage) {
    super(stage);

    myComponent = new JPanel(new BorderLayout());

    myChoreographer = new Choreographer(CHOREOGRAPHER_FPS, myComponent);

    JToolBar toolBar = new JToolBar();
    JButton backButton = new JButton("Go back");
    toolBar.add(backButton);
    toolBar.setFloatable(false);
    backButton.addActionListener(action -> returnToStudioStage());
    myComponent.add(toolBar, BorderLayout.PAGE_START);

    LineChart lineChart = new LineChart();
    lineChart.addLines(stage.getRangedSeries());
    myChoreographer.register(lineChart);

    myComponent.add(lineChart, BorderLayout.CENTER);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
