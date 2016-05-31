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

package com.android.tools.adtui.visual;

import com.android.tools.adtui.visual.flamegraph.FlameGraphVisualTest;
import com.android.tools.adtui.visual.threadgraph.ThreadCallsVisualTest;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;

public class VisualTests {

  interface Value {
    void set(int v);

    int get();
  }

  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        VisualTestsDialog dialog = new VisualTestsDialog();
        dialog.addTest(new FlameGraphVisualTest());
        dialog.addTest(new AccordionVisualTest());
        dialog.addTest(new ThreadCallsVisualTest());
        dialog.addTest(new AxisLineChartVisualTest());
        dialog.addTest(new StateChartVisualTest());
        dialog.addTest(new LineChartVisualTest());
        dialog.addTest(new SunburstVisualTest());
        dialog.addTest(new TimelineVisualTest());
        dialog.addTest(new EventVisualTest());
        dialog.setTitle("Visual Tests");
        dialog.pack();
        dialog.setVisible(true);
      }
    });
    System.exit(0);
  }
}
