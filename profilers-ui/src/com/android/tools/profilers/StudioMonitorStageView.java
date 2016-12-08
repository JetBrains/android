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
import com.android.tools.adtui.TabularLayout;
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

  public StudioMonitorStageView(@NotNull StudioProfilersView profilersView, @NotNull StudioMonitorStage stage) {
    super(profilersView, stage);

    ViewBinder<StudioProfilersView, ProfilerMonitor, ProfilerMonitorView> binder = new ViewBinder<>();
    binder.bind(NetworkMonitor.class, NetworkMonitorView::new);
    binder.bind(CpuMonitor.class, CpuMonitorView::new);
    binder.bind(MemoryMonitor.class, MemoryMonitorView::new);
    binder.bind(EventMonitor.class, EventMonitorView::new);

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(getChoreographer(), getTimeline(), getComponent());
    getChoreographer().register(sb);
    getComponent().add(sb, BorderLayout.SOUTH);

    // Create a 2-row panel. First row, all monitors; second row, the timeline. This way, the
    // timeline will always be at the bottom, even if no monitors are found (e.g. when the phone is
    // disconnected).
    JPanel topPanel = new JPanel(new TabularLayout("*", "*,Fit"));
    topPanel.setBackground(ProfilerColors.MONITOR_BACKGROUND);

    TabularLayout layout = new TabularLayout("*");
    JPanel monitors = new JPanel(layout);

    int rowIndex = 0;
    for (ProfilerMonitor monitor : stage.getMonitors()) {
      ProfilerMonitorView view = binder.build(profilersView, monitor);
      JComponent component = view.initialize(getChoreographer());
      int weight = (int)(view.getVerticalWeight() * 100f);
      layout.setRowSizing(rowIndex, (weight > 0) ? weight + "*" : "Fit");
      monitors.add(component, new TabularLayout.Constraint(rowIndex, 0));
      rowIndex++;
    }

    StudioProfilers profilers = stage.getStudioProfilers();
    AxisComponent timeAxis = buildTimeAxis(profilers);

    getChoreographer().register(timeAxis);
    topPanel.add(monitors, new TabularLayout.Constraint(0, 0));
    topPanel.add(timeAxis, new TabularLayout.Constraint(1, 0));

    getComponent().add(topPanel, BorderLayout.CENTER);
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
