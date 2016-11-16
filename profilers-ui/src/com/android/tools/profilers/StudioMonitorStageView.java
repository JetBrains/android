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

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.profilers.cpu.CpuMonitor;
import com.android.tools.profilers.cpu.CpuMonitorView;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.memory.MemoryMonitor;
import com.android.tools.profilers.memory.MemoryMonitorView;
import com.android.tools.profilers.network.NetworkMonitor;
import com.android.tools.profilers.network.NetworkMonitorView;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Bird eye view displaying high-level information across all profilers.
 */
public class StudioMonitorStageView extends StageView {

  private static final int TIME_AXIS_HEIGHT = JBUI.scale(20);

  private final JPanel myComponent;

  public StudioMonitorStageView(@NotNull StudioMonitorStage stage) {
    super(stage);

    ViewBinder<ProfilerMonitor, ProfilerMonitorView> binder = new ViewBinder<>();
    binder.bind(NetworkMonitor.class, NetworkMonitorView::new);
    binder.bind(CpuMonitor.class, CpuMonitorView::new);
    binder.bind(MemoryMonitor.class, MemoryMonitorView::new);
    binder.bind(EventMonitor.class, EventMonitorView::new);

    myComponent = new JPanel(new BorderLayout());

    Choreographer choreographer = new Choreographer(myComponent);
    JPanel monitors = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;

    int y = 0;
    for (ProfilerMonitor monitor : stage.getMonitors()) {
      ProfilerMonitorView view = binder.build(monitor);
      JComponent component = view.initialize(choreographer);
      gbc.weighty = view.getVerticalWeight();
      gbc.gridy = y++;
      monitors.add(component, gbc);
    }

    AxisComponent.Builder builder = new AxisComponent.Builder(stage.getStudioProfilers().getViewRange(), TimeAxisFormatter.DEFAULT,
                                                              AxisComponent.AxisOrientation.BOTTOM);
    builder.setGlobalRange(stage.getStudioProfilers().getDataRange()).showAxisLine(false)
      .setOffset(stage.getStudioProfilers().getDeviceStartUs());
    AxisComponent timeAxis = builder.build();
    choreographer.register(timeAxis);
    timeAxis.setMinimumSize(new Dimension(Integer.MAX_VALUE, TIME_AXIS_HEIGHT));
    gbc.weighty = 0;
    gbc.gridy = y;
    monitors.add(timeAxis, gbc);

    myComponent.add(monitors, BorderLayout.CENTER);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }
}
