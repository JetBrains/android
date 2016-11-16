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
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;

public class CpuProfilerStageView extends StageView {

  private final JPanel myComponent;
  private final JBList myThreadsList;

  public CpuProfilerStageView(@NotNull CpuProfilerStage stage) {
    super(stage);

    StudioProfilers profilers = stage.getStudioProfilers();
    EventMonitor events = new EventMonitor(profilers);
    EventMonitorView eventsView = new EventMonitorView(events);

    CpuMonitor cpu = new CpuMonitor(profilers);

    myComponent = new JPanel(new GridBagLayout());
    myComponent.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    Choreographer choreographer = new Choreographer(myComponent);

    JComponent eventsComponent = eventsView.initialize(choreographer);

    Range leftYRange = new Range(0, 100);
    LineChart lineChart = new LineChart();
    lineChart.addLine(new RangedContinuousSeries("App", stage.getStudioProfilers().getViewRange(), leftYRange, cpu.getThisProcessCpuUsage()),
                      new LineConfig(ProfilerColors.CPU_USAGE).setFilled(true).setStacked(true));
    lineChart.addLine(new RangedContinuousSeries("Others", stage.getStudioProfilers().getViewRange(), leftYRange, cpu.getOtherProcessesCpuUsage()),
                      new LineConfig(ProfilerColors.CPU_OTHER_USAGE).setFilled(true).setStacked(true));

    RangedListModel<CpuThreadsModel.RangedCpuThread> model = cpu.getThreadStates();
    myThreadsList = new JBList(model);
    myThreadsList.setCellRenderer(new ThreadCellRenderer(choreographer, myThreadsList));
    RangedList rangledList = new RangedList(profilers.getViewRange(), model);

    // TODO: Event monitor should be fixed size.
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.weighty = 0.2;
    myComponent.add(eventsComponent, c);
    c.gridy = 1;
    c.weighty = 0.4;
    myComponent.add(lineChart, c);
    choreographer.register(lineChart);
    c.gridy = 2;
    c.weighty = 0.4;
    myComponent.add(myThreadsList, c);
    choreographer.register(rangledList);
  }


  static class ThreadCellRenderer implements ListCellRenderer<CpuThreadsModel.RangedCpuThread> {

    final JLabel myLabel;
    AnimatedListRenderer<CpuThreadsModel.RangedCpuThread, StateChart<CpuProfiler.ThreadActivity.State>> myStateCharts;

    public ThreadCellRenderer(Choreographer choreographer, JList<CpuThreadsModel.RangedCpuThread> list) {
      myLabel = new JLabel();
      myStateCharts = new AnimatedListRenderer<>(choreographer, list, thread -> {
        StateChart<CpuProfiler.ThreadActivity.State> chart = new StateChart<>(getThreadStateColor());
        chart.addSeries(thread.getDataSeries());
        return chart;
      });
    }

    @Override
    public Component getListCellRendererComponent(JList list,
                                                  CpuThreadsModel.RangedCpuThread value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      // TODO: Place holder rendering
      JPanel panel = new JPanel(new BorderLayout());
      myLabel.setText(value.getName());
      panel.setBackground(isSelected ? Color.BLUE : Color.WHITE);
      panel.add(myLabel, BorderLayout.WEST);
      panel.add(myStateCharts.get(index), BorderLayout.CENTER);
      return panel;
    }
  }

  @NotNull
  private static EnumMap<CpuProfiler.ThreadActivity.State, Color> getThreadStateColor() {
    EnumMap<CpuProfiler.ThreadActivity.State, Color> map = new EnumMap<>(CpuProfiler.ThreadActivity.State.class);
    map.put(CpuProfiler.ThreadActivity.State.RUNNING, new JBColor(new Color(134, 199, 144), new Color(134, 199, 144)));
    map.put(CpuProfiler.ThreadActivity.State.SLEEPING, new JBColor(Gray._189, Gray._189));
    map.put(CpuProfiler.ThreadActivity.State.DEAD, Gray.TRANSPARENT);
    return map;
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
