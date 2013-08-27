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
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.tools.perflib.vmtrace.viz.TraceViewCanvas;
import com.android.utils.SparseArray;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class TraceViewPanel {
  private TraceViewCanvas myTraceViewCanvas;
  private JPanel myContainer;
  private JComboBox myThreadCombo;

  public TraceViewPanel() {
    myThreadCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTraceViewCanvas.displayThread((String)myThreadCombo.getSelectedItem());
      }
    });
  }

  public void setTrace(@NotNull VmTraceData trace) {
    List<String> threadNames = getThreadsWithTraces(trace);
    String threadName = !threadNames.isEmpty() ? threadNames.get(0) : "";
    myTraceViewCanvas.setTrace(trace, threadName);
    myThreadCombo.setModel(new DefaultComboBoxModel(threadNames.toArray()));
    myThreadCombo.setEnabled(true);
  }

  private static List<String> getThreadsWithTraces(@NotNull VmTraceData trace) {
    SparseArray<String> threads = trace.getThreads();
    List<String> threadNames = new ArrayList<String>(threads.size());

    for (int i = 0; i < threads.size(); i++) {
      Call topLevelCall = trace.getTopLevelCall(threads.keyAt(i));
      if (topLevelCall != null) {
        threadNames.add(threads.valueAt(i));
      }
    }
    return threadNames;
  }

  @NotNull
  public JComponent getComponent() {
    return myContainer;
  }
}
