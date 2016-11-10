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
package com.android.tools.profilers.cpu;

import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioMonitorStage;
import com.android.tools.profilers.StudioProfilers;

import javax.swing.*;

public class CpuMonitorStageView extends StageView {
  public CpuMonitorStageView(CpuMonitorStage stage) {
    super(stage);
  }

  @Override
  public JComponent getComponent() {
    JPanel panel = new JPanel();
    panel.add(new JLabel("TODO: CPU L2"));
    JButton button = new JButton("Go back");
    button.addActionListener(action -> {
      StudioProfilers profiler = getStage().getStudioProfiler();
      StudioMonitorStage monitor = new StudioMonitorStage(profiler);
      profiler.setStage(monitor);
    });
    panel.add(button);
    return panel;
  }
}
