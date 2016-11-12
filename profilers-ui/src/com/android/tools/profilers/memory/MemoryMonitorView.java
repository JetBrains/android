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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.profilers.ProfilerMonitorView;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MemoryMonitorView extends ProfilerMonitorView {
  private final MemoryMonitor myMonitor;

  public MemoryMonitorView(@NotNull MemoryMonitor monitor) {
    myMonitor = monitor;
  }

  @Override
  public AnimatedComponent initialize() {
    LineChart lineChart = new LineChart();
    lineChart.addLine(myMonitor.getTotalMemory());
    lineChart.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myMonitor.expand();
      }
    });
    return lineChart;
  }
}
