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

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Choreographer;
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

public class StudioMonitorStageView extends StageView {

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
    JPanel monitors = new JPanel(new GridBagLayout());
    int y = 0;
    for (ProfilerMonitor monitor : stage.getMonitors()) {
      ProfilerMonitorView view = myBinder.build(monitor);
      JComponent component = view.initialize(choreographer);
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.BOTH;
      c.gridx = 0;
      c.gridy = y++;
      c.weightx = 1.0;
      c.weighty = 1.0 / stage.getMonitors().size();
      monitors.add(component, c);
    }
    myComponent.add(monitors, BorderLayout.CENTER);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
