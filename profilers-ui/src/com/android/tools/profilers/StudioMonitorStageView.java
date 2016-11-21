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
import com.android.tools.profilers.cpu.CpuMonitor;
import com.android.tools.profilers.cpu.CpuMonitorView;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.memory.MemoryMonitor;
import com.android.tools.profilers.memory.MemoryMonitorView;
import com.android.tools.profilers.network.NetworkMonitor;
import com.android.tools.profilers.network.NetworkMonitorView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Bird eye view displaying high-level information across all profilers.
 */
public class StudioMonitorStageView extends StageView {

  public StudioMonitorStageView(@NotNull StudioMonitorStage stage) {
    super(stage);

    ViewBinder<ProfilerMonitor, ProfilerMonitorView> binder = new ViewBinder<>();
    binder.bind(NetworkMonitor.class, NetworkMonitorView::new);
    binder.bind(CpuMonitor.class, CpuMonitorView::new);
    binder.bind(MemoryMonitor.class, MemoryMonitorView::new);
    binder.bind(EventMonitor.class, EventMonitorView::new);

    setupPanAndZoomListeners(getComponent());

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(getTimeline());
    getChoreographer().register(sb);
    getComponent().add(sb, BorderLayout.SOUTH);

    JPanel monitors = new JPanel(new GridBagLayout());
    monitors.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;

    int y = 0;
    for (ProfilerMonitor monitor : stage.getMonitors()) {
      ProfilerMonitorView view = binder.build(monitor);
      JComponent component = view.initialize(getChoreographer());
      gbc.weighty = view.getVerticalWeight();
      gbc.gridy = y++;
      monitors.add(component, gbc);
    }

    StudioProfilers profilers = stage.getStudioProfilers();
    AxisComponent timeAxis = buildTimeAxis(profilers);

    getChoreographer().register(timeAxis);
    gbc.weighty = 0;
    gbc.gridy = y;
    monitors.add(timeAxis, gbc);

    getComponent().add(monitors, BorderLayout.CENTER);
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
