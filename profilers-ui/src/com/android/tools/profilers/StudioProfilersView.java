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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStageView;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStageView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiFunction;

public class StudioProfilersView extends AspectObserver {
  private final StudioProfilers myProfiler;
  private final ViewBinder<StudioProfilersView, Stage, StageView> myBinder;
  private StageView myStageView;
  private BorderLayout myLayout;
  private JPanel myComponent;
  private JPanel myStageToolbar;
  private JPanel myProcessSelection;

  @NotNull
  private final IdeProfilerComponents myIdeProfilerComponents;

  public StudioProfilersView(@NotNull StudioProfilers profiler, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myProfiler = profiler;
    myIdeProfilerComponents = ideProfilerComponents;
    myStageView = null;
    initializeUi();

    myBinder = new ViewBinder<>();
    myBinder.bind(StudioMonitorStage.class, StudioMonitorStageView::new);
    myBinder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    myBinder.bind(MemoryProfilerStage.class, MemoryProfilerStageView::new);
    myBinder.bind(NetworkProfilerStage.class, NetworkProfilerStageView::new);

    myProfiler.addDependency(this).onChange(ProfilerAspect.STAGE, this::updateStageView);
  }

  @VisibleForTesting
  public <S extends Stage, T extends StageView> void bind(@NotNull Class<S> clazz, @NotNull BiFunction<StudioProfilersView, S, T> constructor) {
    myBinder.bind(clazz, constructor);
  }

  @VisibleForTesting
  public StageView getStageView() {
    return myStageView;
  }

  private void initializeUi() {
    myLayout = new BorderLayout();
    myComponent = new JPanel(myLayout);

    JComboBox<Profiler.Device> deviceCombo = new ComboBox<>();
    JComboBoxView devices = new JComboBoxView<>(deviceCombo, myProfiler, ProfilerAspect.DEVICES,
                                                myProfiler::getDevices,
                                                myProfiler::getDevice,
                                                myProfiler::setDevice);
    devices.bind();
    deviceCombo.setRenderer(new DeviceComboBoxRenderer());

    JComboBox<Profiler.Process> processCombo = new ComboBox<>();
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

    myStageView = myBinder.build(this, stage);
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

  @VisibleForTesting
  public static class DeviceComboBoxRenderer extends ColoredListCellRenderer<Profiler.Device> {

    @NotNull
    private final String myEmptyText = "No Connected Devices";

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Profiler.Device value, int index,
                                         boolean selected, boolean hasFocus) {
      if (value != null) {
        renderDeviceName(value);
      }
      else {
        append(getEmptyText(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    public void renderDeviceName(@NotNull Profiler.Device d) {
      // TODO: Share code between here and DeviceRenderer#renderDeviceName
      String manufacturer = d.getManufacturer();
      String model = d.getModel();
      String serial = d.getSerial();
      String suffix = String.format("-%s", serial);
      if (model.endsWith(suffix)) {
        model = model.substring(0, model.length() - suffix.length());
      }
      if (!StringUtil.isEmpty(manufacturer)) {
        append(String.format("%s ", manufacturer), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      append(model, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      append(String.format(" (%1$s)", serial), SimpleTextAttributes.GRAY_ATTRIBUTES);

      Profiler.Device.State state = d.getState();
      if (state != Profiler.Device.State.ONLINE && state != Profiler.Device.State.UNSPECIFIED) {
        append(String.format(" [%s]", state), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      }
    }

    @NotNull
    @VisibleForTesting
    public String getEmptyText() {
      return myEmptyText;
    }
  }

  @VisibleForTesting
  public static class ProcessComboBoxRenderer extends ColoredListCellRenderer<Profiler.Process> {

    @NotNull
    private final String myEmptyText = "No Debuggable Processes";

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Profiler.Process value, int index,
                                         boolean selected, boolean hasFocus) {
      if (value != null) {
        renderProcessName(value);
      }
      else {
        append(getEmptyText(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    private void renderProcessName(@NotNull Profiler.Process process) {
      // TODO: Share code between here and ClientCellRenderer#renderClient
      String name = process.getName();
      // Highlight the last part of the process name.
      int index = name.lastIndexOf('.');
      append(name.substring(0, index + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(name.substring(index + 1), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

      append(String.format(" (%1$d)", process.getPid()), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    @NotNull
    @VisibleForTesting
    public String getEmptyText() {
      return myEmptyText;
    }
  }

  @NotNull
  public IdeProfilerComponents getIdeProfilerComponents() {
    return myIdeProfilerComponents;
  }
}
