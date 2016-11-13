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

import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStageView;
import com.android.tools.profilers.network.NetworkProfilerStageView;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.*;

public class StudioProfilersView {
  private final StudioProfilers myProfiler;
  private final ViewBinder<Stage, StageView> myBinder;
  private StageView myStageView;
  private BorderLayout myLayout;
  private JPanel myComponent;
  private JPanel myStageToolbar;
  private JPanel myProcessSelection;


  public StudioProfilersView(StudioProfilers profiler) {
    myProfiler = profiler;
    myStageView = null;
    initializeUi();

    myBinder = new ViewBinder<>();
    myBinder.bind(StudioMonitorStage.class, StudioMonitorStageView::new);
    myBinder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    myBinder.bind(MemoryProfilerStage.class, MemoryProfilerStageView::new);
    myBinder.bind(NetworkProfilerStage.class, NetworkProfilerStageView::new);

    myProfiler.addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(ProfilerAspect.STAGE, this::updateStageView);
  }

  private void initializeUi() {
    myLayout = new BorderLayout();
    myComponent = new JPanel(myLayout);

    JComboBox<Profiler.Device> deviceCombo = new JComboBox<>();
    JComboBoxView devices = new JComboBoxView<>(deviceCombo, myProfiler, ProfilerAspect.DEVICES,
                                                myProfiler::getDevices,
                                                myProfiler::getDevice,
                                                myProfiler::setDevice);
    devices.bind();

    JComboBox<Profiler.Process> processCombo = new JComboBox<>();
    JComboBoxView processes = new JComboBoxView<>(processCombo, myProfiler, ProfilerAspect.PROCESSES,
                                                  myProfiler::getProcesses,
                                                  myProfiler::getProcess,
                                                  myProfiler::setProcess);
    processes.bind();


    JPanel toolbar = new JPanel(new BorderLayout());

    myProcessSelection = new JPanel();
    myProcessSelection.add(deviceCombo);
    myProcessSelection.add(processCombo);
    toolbar.add(myProcessSelection, BorderLayout.WEST);

    myStageToolbar = new JPanel(new BorderLayout());
    toolbar.add(myStageToolbar, BorderLayout.CENTER);

    myComponent.add(toolbar, BorderLayout.NORTH);
  }

  private void updateStageView() {
    Stage stage = myProfiler.getStage();
    if (myStageView == null || myStageView.getStage() != stage) {
      myStageView = myBinder.build(stage);
      Component prev = myLayout.getLayoutComponent(BorderLayout.CENTER);
      if (prev != null) {
        myComponent.remove(prev);
      }
      myComponent.add(myStageView.getComponent(), BorderLayout.CENTER);
      myComponent.revalidate();

      myStageToolbar.removeAll();
      myStageToolbar.add(myStageView.getToolbar(), BorderLayout.CENTER);
      myStageToolbar.revalidate();

      myProcessSelection.setVisible(myStageView.needsProcessSelection());
    }
  }

  public JPanel getComponent() {
    return myComponent;
  }
}
