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
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class StudioProfilerView {
  private final StudioProfiler myProfiler;
  private ProfilerStageView myStageView;
  private JPanel myComponent;

  private static Map<Class, Class> STAGE_VIEWS = ImmutableMap.of(
    StudioMonitor.class, StudioMonitorView.class
  );

  public StudioProfilerView(StudioProfiler profiler) {
    myProfiler = profiler;
    myStageView = null;
    initializeUi();

    myProfiler.addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(Aspect.STAGE, this::updateStageView);
  }

  private void initializeUi() {
    myComponent = new JPanel(new BorderLayout());

    JComboBox<Profiler.Device> deviceCombo = new JComboBox<>();
    JComboBoxView devices = new JComboBoxView<>(deviceCombo, myProfiler, Aspect.DEVICES,
                                                myProfiler::getDevices,
                                                myProfiler::getDevice,
                                                myProfiler::setDevice);
    devices.bind();

    JComboBox<Profiler.Process> processCombo = new JComboBox<>();
    JComboBoxView processes = new JComboBoxView<>(processCombo, myProfiler, Aspect.DEVICES,
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
    StudioProfilerStage stage = myProfiler.getStage();
    if (myStageView == null || myStageView.getStage() != stage) {
      myStageView = bindView(stage);
      myComponent.add(myStageView.getComponent(), BorderLayout.CENTER);
    }
  }

  public JPanel getComponent() {
    return myComponent;
  }

  private ProfilerStageView bindView(StudioProfilerStage stage) {
    Class aClass = STAGE_VIEWS.get(stage.getClass());
    if (aClass == null) {
      throw new IllegalArgumentException("Stage of type " + stage.getClass().getCanonicalName() + " cannot be bound to a view.");
    }
    try {
      Constructor constructor = aClass.getConstructor(stage.getClass());
      Object instance = constructor.newInstance(stage);
      return (ProfilerStageView)instance;
    }
    catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException | ClassCastException e) {
      throw new IllegalStateException("ProfilerStageView " + aClass.getCanonicalName() + " cannot be instantiated.", e);
    }
  }
}
