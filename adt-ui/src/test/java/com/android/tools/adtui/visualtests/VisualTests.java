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

package com.android.tools.adtui.visualtests;

import com.android.tools.adtui.visualtests.flamegraph.FlameGraphVisualTest;
import com.android.tools.adtui.visualtests.threadgraph.ThreadCallsVisualTest;

import javax.swing.*;

public class VisualTests {

  interface Value {
    void set(int v);

    int get();
  }

  /**
   * Adapter when you want to have a backing field for the
   * value you want to set/get.
   */
  abstract static class ValueAdapter implements Value {
    private int myValue;

    @Override
    public final void set(int v) {
      myValue = v;
      onSet(v);
    }

    @Override
    public final int get() {
      return myValue;
    }

    protected abstract void onSet(int v);
  }

  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      VisualTestsDialog dialog = new VisualTestsDialog();
      dialog.addTest(new SelectionVisualTest());
      dialog.addTest(new BoxSelectionVisualTest());
      dialog.addTest(new TooltipVisualTest());
      dialog.addTest(new LineChartVisualTest());
      dialog.addTest(new FlameGraphVisualTest());
      dialog.addTest(new ThreadCallsVisualTest());
      dialog.addTest(new AxisLineChartVisualTest());
      dialog.addTest(new StateChartVisualTest());
      dialog.addTest(new EventVisualTest());
      dialog.addTest(new LineChartReducerVisualTest());
      dialog.addTest(new StateChartReducerVisualTest());
      dialog.addTest(new HorizontalSpinnerVisualTest());
      dialog.addTest(new HoverColumnTreeVisualTest());
      dialog.setTitle("Visual Tests");
      dialog.pack();
      dialog.setVisible(true);
    });
    System.exit(0);
  }
}
