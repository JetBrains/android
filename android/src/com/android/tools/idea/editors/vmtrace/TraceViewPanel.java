/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.editors.vmtrace;

import com.android.tools.perflib.vmtrace.Call;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.tools.perflib.vmtrace.viz.TraceViewCanvas;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TraceViewPanel {
  /** Default name for main thread in Android apps. */
  @NonNls private static final String MAIN_THREAD_NAME = "main";

  private TraceViewCanvas myTraceViewCanvas;
  private JPanel myContainer;
  private JComboBox myThreadCombo;
  private JComboBox myRenderClockSelectorCombo;

  private static final String[] ourRenderClockOptions = new String[] {
    "Wall Clock Time",
    "Thread Time",
  };

  private static final ClockType[] ourRenderClockTypes = new ClockType[] {
    ClockType.GLOBAL,
    ClockType.THREAD,
  };

  public TraceViewPanel() {
    myRenderClockSelectorCombo.setModel(new DefaultComboBoxModel(ourRenderClockOptions));
    myRenderClockSelectorCombo.setSelectedIndex(0);

    ActionListener l = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == myThreadCombo) {
          myTraceViewCanvas.displayThread((String)myThreadCombo.getSelectedItem());
        } else if (e.getSource() == myRenderClockSelectorCombo) {
          myTraceViewCanvas.setRenderClock(getCurrentRenderClock());
        }
      }
    };

    myThreadCombo.addActionListener(l);
    myRenderClockSelectorCombo.addActionListener(l);
  }

  public void setTrace(@NotNull VmTraceData trace) {
    List<String> threadNames = getThreadsWithTraces(trace);
    String defaultThread = getDefaultThreadName(threadNames);
    myTraceViewCanvas.setTrace(trace, defaultThread, getCurrentRenderClock());
    myThreadCombo.setModel(new DefaultComboBoxModel(threadNames.toArray()));
    myThreadCombo.setSelectedIndex(threadNames.indexOf(defaultThread));

    myThreadCombo.setEnabled(true);
    myRenderClockSelectorCombo.setEnabled(true);
  }

  @NotNull
  private String getDefaultThreadName(@NotNull List<String> threadNames) {
    if (threadNames.isEmpty()) {
      return "";
    }

    // default to displaying info from main thread
    return threadNames.contains(MAIN_THREAD_NAME) ? MAIN_THREAD_NAME : threadNames.get(0);
  }

  private ClockType getCurrentRenderClock() {
    return ourRenderClockTypes[myRenderClockSelectorCombo.getSelectedIndex()];
  }

  private static List<String> getThreadsWithTraces(@NotNull VmTraceData trace) {
    Collection<ThreadInfo> threads = trace.getThreads();
    List<String> threadNames = new ArrayList<String>(threads.size());

    for (ThreadInfo thread : threads) {
      Call topLevelCall = thread.getTopLevelCall();
      if (topLevelCall != null) {
        threadNames.add(thread.getName());
      }
    }
    return threadNames;
  }

  @NotNull
  public JComponent getComponent() {
    return myContainer;
  }
}
