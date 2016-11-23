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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

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
    deviceCombo.setRenderer(new DeviceComboBoxRenderer());

    JComboBox<Profiler.Process> processCombo = new JComboBox<>();
    JComboBoxView processes = new JComboBoxView<>(processCombo, myProfiler, ProfilerAspect.PROCESSES,
                                                  myProfiler::getProcesses,
                                                  myProfiler::getProcess,
                                                  myProfiler::setProcess);
    processes.bind();
    processCombo.setRenderer(new ProcessComboBoxRenderer());

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
    if (myStageView != null && myStageView.getStage() == stage) {
      return;
    }

    if (myStageView != null) {
      myStageView.exit();
    }

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

  public JPanel getComponent() {
    return myComponent;
  }

  private static class DeviceComboBoxRenderer extends ColoredListCellRenderer<Profiler.Device> {

    @NotNull
    private final String myEmptyText = "No Connected Devices";

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Profiler.Device value, int index,
                                         boolean selected, boolean hasFocus) {
      if (value != null) {
        renderDeviceName(value);
      } else {
        append(myEmptyText, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    public void renderDeviceName(@NotNull Profiler.Device d) {
      // As of 2016-11, only model and serial are populated in Profiler.Device.
      // Model seems to be a string in the form of "model-serial". Here we are trying
      // to divide it into real model name and serial number, and then render them nicely.
      // TODO: Render better structured info when more fields are populated in Profiler.Device.
      String model = d.getModel();
      String serial = d.getSerial();
      String suffix = String.format("-%s", serial);
      if (model.endsWith(suffix)) {
        model = model.substring(0, model.length() - suffix.length());
      }
      append(model, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      append(String.format(" (%1$s)", serial), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private static class ProcessComboBoxRenderer extends ColoredListCellRenderer<Profiler.Process> {

    @NotNull
    private final String myEmptyText = "No Debuggable Processes";

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Profiler.Process value, int index,
                                         boolean selected, boolean hasFocus) {
      if (value != null) {
        renderProcessName(value);
      } else {
        append(myEmptyText, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    private void renderProcessName(@NotNull Profiler.Process process) {
      String name = process.getName();
      if (name == null) {
        return;
      }
      // Highlight the last part of the process name.
      int index = name.lastIndexOf('.');
      append(name.substring(0, index + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(name.substring(index + 1), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

      append(String.format(" (%1$d)", process.getPid()), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
