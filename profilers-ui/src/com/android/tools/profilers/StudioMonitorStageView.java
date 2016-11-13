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

import com.android.tools.adtui.AccordionLayout;
import com.android.tools.adtui.AnimatedComponent;
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
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Bird eye view displaying high-level information across all profilers.
 */
public class StudioMonitorStageView extends StageView {

  private static final int MONITOR_MIN_HEIGHT = JBUI.scale(0);
  private static final int MONITOR_MAX_HEIGHT = JBUI.scale(Short.MAX_VALUE);
  private static final int MONITOR_PREFERRED_HEIGHT = JBUI.scale(200);
  private static final int TIME_AXIS_HEIGHT = JBUI.scale(20);
  private static final int CHOREOGRAPHER_FPS = 60;

  private final JPanel myComponent;
  private final ViewBinder<ProfilerMonitor, ProfilerMonitorView> myBinder;

  public StudioMonitorStageView(@NotNull StudioMonitorStage stage) {
    super(stage);
    myBinder = new ViewBinder<>();
    myBinder.bind(NetworkMonitor.class, NetworkMonitorView::new);
    myBinder.bind(CpuMonitor.class, CpuMonitorView::new);
    myBinder.bind(MemoryMonitor.class, MemoryMonitorView::new);
    myBinder.bind(EventMonitor.class, EventMonitorView::new);

    myComponent = new JPanel(new BorderLayout());

    Choreographer choreographer = new Choreographer(CHOREOGRAPHER_FPS, myComponent);
    JPanel monitors = new JPanel();
    AccordionLayout accordion = new AccordionLayout(monitors, AccordionLayout.Orientation.VERTICAL);
    monitors.setLayout(accordion);
    accordion.setLerpFraction(1f);
    choreographer.register(accordion);

    for (ProfilerMonitor monitor : stage.getMonitors()) {
      ProfilerMonitorView view = myBinder.build(monitor);
      JComponent component = view.initialize(choreographer);

      // TODO event monitor should use a fix height
      component.setMinimumSize(new Dimension(0, MONITOR_MIN_HEIGHT));
      component.setPreferredSize(new Dimension(0, MONITOR_PREFERRED_HEIGHT));
      component.setMaximumSize(new Dimension(0, MONITOR_MAX_HEIGHT));
      monitors.add(component);
    }

    AxisComponent.Builder builder = new AxisComponent.Builder(stage.getStudioProfilers().getViewRange(), TimeAxisFormatter.DEFAULT,
                                                              AxisComponent.AxisOrientation.BOTTOM);
    builder.setGlobalRange(stage.getStudioProfilers().getDataRange()).showAxisLine(false).setOffset(stage.getStudioProfilers().getDeviceStartUs());
    AxisComponent timeAxis = builder.build();
    timeAxis.setMinimumSize(new Dimension(0, TIME_AXIS_HEIGHT));
    timeAxis.setPreferredSize(new Dimension(0, TIME_AXIS_HEIGHT));
    timeAxis.setMaximumSize(new Dimension(0, TIME_AXIS_HEIGHT));
    monitors.add(timeAxis);
    choreographer.register(timeAxis);

    myComponent.add(monitors, BorderLayout.CENTER);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
