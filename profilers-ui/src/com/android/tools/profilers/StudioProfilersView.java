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

import com.android.tools.adtui.flat.FlatButton;
import com.android.tools.adtui.flat.FlatComboBox;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.flat.FlatToggleButton;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.energy.EnergyProfilerStageView;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStageView;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStageView;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiFunction;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;

public class StudioProfilersView extends AspectObserver {
  private final StudioProfilers myProfiler;
  private final ViewBinder<StudioProfilersView, Stage, StageView> myBinder;
  private StageView myStageView;
  private final BorderLayout myLayout;
  private final JPanel myComponent;
  private JPanel myStageToolbar;
  private JPanel myMonitoringToolbar;
  private JPanel myCommonToolbar;
  private AbstractButton myGoLive;

  @NotNull
  private final IdeProfilerComponents myIdeProfilerComponents;

  public StudioProfilersView(@NotNull StudioProfilers profiler, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myProfiler = profiler;
    myIdeProfilerComponents = ideProfilerComponents;
    myStageView = null;
    myLayout = new BorderLayout();
    myComponent = new JPanel(myLayout);

    initializeUi();

    myBinder = new ViewBinder<>();
    myBinder.bind(StudioMonitorStage.class, StudioMonitorStageView::new);
    myBinder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    myBinder.bind(MemoryProfilerStage.class, MemoryProfilerStageView::new);
    myBinder.bind(NetworkProfilerStage.class, NetworkProfilerStageView::new);
    myBinder.bind(NullMonitorStage.class, NullMonitorStageView::new);
    myBinder.bind(EnergyProfilerStage.class, EnergyProfilerStageView::new);

    myProfiler.addDependency(this).onChange(ProfilerAspect.STAGE, this::updateStageView);
    updateStageView();
  }

  @VisibleForTesting
  public <S extends Stage, T extends StageView> void bind(@NotNull Class<S> clazz,
                                                          @NotNull BiFunction<StudioProfilersView, S, T> constructor) {
    myBinder.bind(clazz, constructor);
  }

  @VisibleForTesting
  public StageView getStageView() {
    return myStageView;
  }

  private void initializeUi() {
    JComboBox<Common.Device> deviceCombo = new FlatComboBox<>();
    JComboBoxView devices = new JComboBoxView<>(deviceCombo, myProfiler, ProfilerAspect.DEVICES,
                                                myProfiler::getDevices,
                                                myProfiler::getDevice,
                                                myProfiler::setDevice);
    myProfiler.addDependency(this)
      .onChange(ProfilerAspect.DEVICES, () -> myProfiler.getIdeServices().getFeatureTracker().trackChangeDevice(myProfiler.getDevice()));
    devices.bind();
    deviceCombo.setRenderer(new DeviceComboBoxRenderer());

    JComboBox<Common.Process> processCombo = new FlatComboBox<>();
    JComboBoxView processes = new JComboBoxView<>(processCombo, myProfiler, ProfilerAspect.PROCESSES,
                                                  myProfiler::getProcesses,
                                                  myProfiler::getProcess,
                                                  myProfiler::setProcess);
    myProfiler.addDependency(this)
      .onChange(ProfilerAspect.PROCESSES,
                () -> myProfiler.getIdeServices().getFeatureTracker().trackChangeProcess(myProfiler.getProcess()));
    processes.bind();
    processCombo.setRenderer(new ProcessComboBoxRenderer());

    JPanel toolbar = new JPanel(new BorderLayout());
    JPanel leftToolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));

    toolbar.setBorder(DEFAULT_BOTTOM_BORDER);
    // There is a border of 5 pixels that is not scaled.
    toolbar.setPreferredSize(new Dimension(15, JBUI.scale(25) + 5));

    myMonitoringToolbar = new JPanel(ProfilerLayout.TOOLBAR_LAYOUT);
    myMonitoringToolbar.add(deviceCombo);
    myMonitoringToolbar.add(processCombo);

    myCommonToolbar = new JPanel(ProfilerLayout.TOOLBAR_LAYOUT);
    JButton button = new FlatButton(StudioIcons.Common.BACK_ARROW);
    button.addActionListener(action -> {
      myProfiler.setMonitoringStage();
      myProfiler.getIdeServices().getFeatureTracker().trackGoBack();
    });
    myCommonToolbar.add(button);
    myCommonToolbar.add(new FlatSeparator());

    JComboBox<Class<? extends Stage>> stageCombo = new FlatComboBox<>();
    JComboBoxView stages = new JComboBoxView<>(stageCombo, myProfiler, ProfilerAspect.STAGE,
                                               myProfiler::getDirectStages,
                                               myProfiler::getStageClass,
                                               stage -> {
                                                 // Track first, so current stage is sent with the event
                                                 myProfiler.getIdeServices().getFeatureTracker().trackSelectMonitor();
                                                 myProfiler.setNewStage(stage);
                                               });
    stageCombo.setRenderer(new StageComboBoxRenderer());
    stages.bind();
    myCommonToolbar.add(stageCombo);
    myCommonToolbar.add(new FlatSeparator());

    leftToolbar.add(myMonitoringToolbar);
    leftToolbar.add(myCommonToolbar);
    toolbar.add(leftToolbar, BorderLayout.WEST);

    JPanel rightToolbar = new JPanel(ProfilerLayout.TOOLBAR_LAYOUT);
    toolbar.add(rightToolbar, BorderLayout.EAST);
    rightToolbar.setBorder(new JBEmptyBorder(0, 0, 0, 2));

    FlatButton endSession = new FlatButton("End Session");
    endSession.setFont(endSession.getFont().deriveFont(12.f));
    endSession.setBorder(new JBEmptyBorder(4, 7, 4, 7));
    endSession.addActionListener(event -> myProfiler.stop());
    endSession.setToolTipText("Stop profiling and close tab");
    rightToolbar.add(endSession);

    rightToolbar.add(new FlatSeparator());

    ProfilerTimeline timeline = myProfiler.getTimeline();
    FlatButton zoomOut = new FlatButton(StudioIcons.Common.ZOOM_OUT);
    zoomOut.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_OUT));
    zoomOut.addActionListener(event -> {
      timeline.zoomOut();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomOut();
    });
    zoomOut.setToolTipText("Zoom out");
    rightToolbar.add(zoomOut);

    FlatButton zoomIn = new FlatButton(StudioIcons.Common.ZOOM_IN);
    zoomIn.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_IN));
    zoomIn.addActionListener(event -> {
      timeline.zoomIn();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomIn();
    });
    zoomIn.setToolTipText("Zoom in");
    rightToolbar.add(zoomIn);

    FlatButton resetZoom = new FlatButton(StudioIcons.Common.RESET_ZOOM);
    resetZoom.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM));
    resetZoom.addActionListener(event -> {
      timeline.resetZoom();
      myProfiler.getIdeServices().getFeatureTracker().trackResetZoom();
    });
    resetZoom.setToolTipText("Reset zoom");
    rightToolbar.add(resetZoom);
    rightToolbar.add(new FlatSeparator());

    myGoLive = new FlatToggleButton("Live", StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    myGoLive.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.GOTO_LIVE));
    myGoLive.setToolTipText("See realtime profiler data");
    myGoLive.setFont(myGoLive.getFont().deriveFont(13.f));
    myGoLive.setHorizontalTextPosition(SwingConstants.LEFT);
    myGoLive.setHorizontalAlignment(SwingConstants.LEFT);
    myGoLive.setBorder(new JBEmptyBorder(3, 8, 3, 7));
    myGoLive.setIconTextGap(JBUI.scale(8));
    myGoLive.addActionListener(event -> {
      timeline.toggleStreaming();
      myProfiler.getIdeServices().getFeatureTracker().trackToggleStreaming();
    });
    timeline.addDependency(this).onChange(ProfilerTimeline.Aspect.STREAMING, this::updateStreaming);
    rightToolbar.add(myGoLive);

    Runnable toggleToolButtons = () -> {
      zoomOut.setEnabled(myProfiler.isProcessAlive());
      zoomIn.setEnabled(myProfiler.isProcessAlive());
      resetZoom.setEnabled(myProfiler.isProcessAlive());
      myGoLive.setEnabled(myProfiler.isProcessAlive());
    };
    myProfiler.addDependency(this).onChange(ProfilerAspect.PROCESSES, toggleToolButtons);
    toggleToolButtons.run();

    myStageToolbar = new JPanel(new BorderLayout());
    toolbar.add(myStageToolbar, BorderLayout.CENTER);

    myComponent.add(toolbar, BorderLayout.NORTH);

    updateStreaming();
  }

  private void updateStreaming() {
    myGoLive.setSelected(myProfiler.getTimeline().isStreaming());
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

    boolean topLevel = myStageView == null || myStageView.needsProcessSelection();
    myMonitoringToolbar.setVisible(topLevel);
    myCommonToolbar.setVisible(!topLevel);
  }

  public JPanel getComponent() {
    return myComponent;
  }

  @VisibleForTesting
  public static class DeviceComboBoxRenderer extends ColoredListCellRenderer<Common.Device> {

    @NotNull
    private final String myEmptyText = "No connected devices";

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Common.Device value, int index,
                                         boolean selected, boolean hasFocus) {
      if (value != null) {
        renderDeviceName(value);
      }
      else {
        append(getEmptyText(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    public void renderDeviceName(@NotNull Common.Device d) {
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

      Common.Device.State state = d.getState();
      if (state != Common.Device.State.ONLINE && state != Common.Device.State.UNSPECIFIED) {
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
  public static class ProcessComboBoxRenderer extends ColoredListCellRenderer<Common.Process> {

    @NotNull
    private final String myEmptyText = "No debuggable processes";

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Common.Process value, int index,
                                         boolean selected, boolean hasFocus) {
      if (value != null) {
        renderProcessName(value);
      }
      else {
        append(getEmptyText(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    private void renderProcessName(@NotNull Common.Process process) {
      // TODO: Share code between here and ClientCellRenderer#renderClient
      String name = process.getName();
      // Highlight the last part of the process name.
      int index = name.lastIndexOf('.');
      append(name.substring(0, index + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(name.substring(index + 1), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

      append(String.format(" (%1$d)", process.getPid()), SimpleTextAttributes.GRAY_ATTRIBUTES);

      if (process.getState() != Common.Process.State.ALIVE) {
        append(" [DEAD]", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      }
    }

    @NotNull
    @VisibleForTesting
    public String getEmptyText() {
      return myEmptyText;
    }
  }

  @VisibleForTesting
  public static class StageComboBoxRenderer extends ColoredListCellRenderer<Class> {

    private static ImmutableMap<Class<? extends Stage>, String> CLASS_TO_NAME = ImmutableMap.of(
      CpuProfilerStage.class, "CPU",
      MemoryProfilerStage.class, "MEMORY",
      NetworkProfilerStage.class, "NETWORK",
      EnergyProfilerStage.class, "ENERGY");

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Class value, int index, boolean selected, boolean hasFocus) {
      String name = CLASS_TO_NAME.get(value);
      append(name == null ? "[UNKNOWN]" : name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }


  @NotNull
  public IdeProfilerComponents getIdeProfilerComponents() {
    return myIdeProfilerComponents;
  }
}
