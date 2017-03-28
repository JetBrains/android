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
package com.android.tools.idea.monitor.ui.cpu.view;

import com.android.tools.idea.monitor.tool.TraceRequestHandler;
import com.intellij.openapi.ui.ComboBox;
import icons.AndroidIcons;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class CPUTraceController extends JPanel implements ItemListener, ActionListener {

  private static final String PROFILER_ART = "ART";
  private static final String PROFILER_SIMPLE_PERF = "Simpleperf";

  private static final String MODE_SAMPLING = "Sampling";
  private static final String MODE_INSTRUMENTING = "Instrumenting";

  private static final String START_TRACING = "Start tracing";
  private static final String STOP_TRACING = "Stop tracing";

  private JButton myActionButtion;
  private JComboBox<String> myProfilers;
  private JComboBox<String> myProfilingModes;

  private final TraceRequestHandler myTraceRequestHandler;

  private boolean myTracing;

  public CPUTraceController(TraceRequestHandler traceRequestHandler) {
    super();
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

    myTraceRequestHandler = traceRequestHandler;

    myActionButtion = new JButton();
    myActionButtion.setVisible(true);
    myActionButtion.setIcon(AndroidIcons.Ddms.StartMethodProfiling);
    myActionButtion.setText(START_TRACING);
    myActionButtion.addActionListener(this);
    add(myActionButtion);

    myProfilers = new ComboBox();
    myProfilers.addItem(PROFILER_ART);
    myProfilers.addItem(PROFILER_SIMPLE_PERF);
    myProfilers.addItemListener(this);
    add(myProfilers);

    myProfilingModes = new ComboBox();
    myProfilingModes.addItem(MODE_SAMPLING);
    myProfilingModes.addItem(MODE_INSTRUMENTING);
    add(myProfilingModes);

    myTracing = false;
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    if (myProfilers.getSelectedItem().equals(PROFILER_SIMPLE_PERF)) {
      myProfilingModes.setSelectedItem(MODE_SAMPLING);
      myProfilingModes.setEnabled(false);
    }
    else {
      myProfilingModes.setEnabled(true);
    }
  }

  private void startTracing() {

    TraceRequestHandler.Profiler profiler;
    if (myProfilers.getSelectedItem().equals(PROFILER_ART)) {
      profiler = TraceRequestHandler.Profiler.ART;
    }
    else {
      profiler = TraceRequestHandler.Profiler.SIMPLEPERF;
    }

    TraceRequestHandler.Mode mode;
    if (myProfilingModes.getSelectedItem().equals(MODE_SAMPLING)) {
      mode = TraceRequestHandler.Mode.SAMPLING;
    }
    else {
      mode = TraceRequestHandler.Mode.INSTRUMENTING;
    }

    myTraceRequestHandler.startTracing(profiler, mode);
    myActionButtion.setText(STOP_TRACING);
    myTracing = true;
  }

  private void stopTracing() {
    myActionButtion.setText(START_TRACING);
    myTracing = false;

    TraceRequestHandler.Profiler profiler;
    if (myProfilers.getSelectedItem().equals(PROFILER_ART)) {
      profiler = TraceRequestHandler.Profiler.ART;
    }
    else {
      profiler = TraceRequestHandler.Profiler.SIMPLEPERF;
    }
    myTraceRequestHandler.stopTracing(profiler);
  }

  @Override
  public void actionPerformed(ActionEvent ae) {
    if (myTracing) {
      stopTracing();
    }
    else {
      startTracing();
    }
  }
}
