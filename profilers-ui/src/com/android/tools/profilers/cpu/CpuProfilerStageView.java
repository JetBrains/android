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
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.profilers.StudioMonitorStageView.CHOREOGRAPHER_FPS;

public class CpuProfilerStageView extends StageView {

  private final JPanel myComponent;

  public CpuProfilerStageView(@NotNull CpuProfilerStage stage) {
    super(stage);

    StudioProfilers profilers = stage.getStudioProfilers();
    EventMonitor events = new EventMonitor(profilers);
    EventMonitorView eventsView = new EventMonitorView(events);

    CpuMonitor cpu = new CpuMonitor(profilers);

    myComponent = new JPanel(new GridBagLayout());
    myComponent.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    Choreographer choreographer = new Choreographer(CHOREOGRAPHER_FPS, myComponent);

    JComponent eventsComponent = eventsView.initialize(choreographer);

    LineChart lineChart = new LineChart();
    LineConfig config = new LineConfig(ProfilerColors.CPU_USAGE).setFilled(true).setStacked(true);
    lineChart.addLine(cpu.getCpuUsage(false), config);
    config = new LineConfig(ProfilerColors.CPU_OTHER_USAGE).setFilled(true).setStacked(true);
    lineChart.addLine(cpu.getCpuUsage(true), config);

    // TODO: Event monitor should be fixed size.
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.weighty = 0.2;
    myComponent.add(eventsComponent, c);
    c.gridy = 1;
    c.weighty = 0.8;
    myComponent.add(lineChart, c);
    choreographer.register(lineChart);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public JComponent getToolbar() {
    JPanel panel = new JPanel(new BorderLayout());

    JPanel toolbar = new JPanel();

    JButton button = new JButton("<-");
    button.addActionListener(action -> returnToStudioStage());
    toolbar.add(button);

    JButton capture = new JButton("Capture");
    toolbar.add(capture);

    panel.add(toolbar, BorderLayout.WEST);
    return panel;
  }
}
