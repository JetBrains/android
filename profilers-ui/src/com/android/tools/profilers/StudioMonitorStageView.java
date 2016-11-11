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
package com.android.tools.profilers;

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.chart.linechart.LineChart;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class StudioMonitorStageView extends StageView {

  private static final int CHOREOGRAPHER_FPS = 60;
  private final Choreographer myChoreographer;
  private final JPanel myComponent;

  public StudioMonitorStageView(StudioMonitorStage stage) {
    super(stage);
    myComponent = new JPanel(new BorderLayout());
    myChoreographer = new Choreographer(CHOREOGRAPHER_FPS, myComponent);
    JPanel monitors = new JPanel(new GridBagLayout());
    //TODO Have this in separate sections in the view
    int y = 0;
    for (ProfilerMonitor monitor : stage.getMonitors()) {
      LineChart lineChart = new LineChart();
      lineChart.addLine(monitor.getRangedSeries());
      myChoreographer.register(lineChart);
      lineChart.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          stage.expand(monitor);
        }
      });
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.BOTH;
      c.gridx = 0;
      c.gridy = y++;
      c.weightx = 1.0;
      c.weighty = 1.0 / stage.getMonitors().size();
      monitors.add(lineChart, c);
    }
    myComponent.add(monitors, BorderLayout.CENTER);
  }


  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
