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
import com.android.tools.profilers.cpu.CpuMonitorStage;
import com.android.tools.profilers.cpu.CpuMonitorStageView;
import com.android.tools.profilers.network.NetworkMonitorStage;
import com.android.tools.profilers.network.NetworkMonitorStageView;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class StudioProfilersView {
  private final StudioProfilers myProfiler;
  private StageView myStageView;
  private BorderLayout myLayout;
  private JPanel myComponent;

  private static Map<Class, Class> STAGE_VIEWS = ImmutableMap.of(
    StudioMonitorStage.class, StudioMonitorStageView.class,
    CpuMonitorStage.class, CpuMonitorStageView.class,
    NetworkMonitorStage.class, NetworkMonitorStageView.class
  );

  public StudioProfilersView(StudioProfilers profiler) {
    myProfiler = profiler;
    myStageView = null;
    initializeUi();

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
    JComboBoxView processes = new JComboBoxView<>(processCombo, myProfiler, ProfilerAspect.DEVICES,
                                                  myProfiler::getProcesses,
                                                  myProfiler::getProcess,
                                                  myProfiler::setProcess);
    processes.bind();

    JPanel toolbar = new JPanel(new BorderLayout());
    JPanel panel = new JPanel();
    panel.add(deviceCombo);
    panel.add(processCombo);
    toolbar.add(panel, BorderLayout.WEST);
    myComponent.add(toolbar, BorderLayout.NORTH);
  }

  private void updateStageView() {
    Stage stage = myProfiler.getStage();
    if (myStageView == null || myStageView.getStage() != stage) {
      myStageView = bindView(stage);
      Component prev = myLayout.getLayoutComponent(BorderLayout.CENTER);
      if (prev != null) {
        myComponent.remove(prev);
      }
      myComponent.add(myStageView.getComponent(), BorderLayout.CENTER);
      myComponent.revalidate();
    }
  }

  public JPanel getComponent() {
    return myComponent;
  }

  private StageView bindView(Stage stage) {
    Class aClass = STAGE_VIEWS.get(stage.getClass());
    if (aClass == null) {
      throw new IllegalArgumentException("Stage of type " + stage.getClass().getCanonicalName() + " cannot be bound to a view.");
    }
    try {
      Constructor constructor = aClass.getConstructor(stage.getClass());
      Object instance = constructor.newInstance(stage);
      return (StageView)instance;
    }
    catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException | ClassCastException e) {
      throw new IllegalStateException("ProfilerStageView " + aClass.getCanonicalName() + " cannot be instantiated.", e);
    }
  }
}
