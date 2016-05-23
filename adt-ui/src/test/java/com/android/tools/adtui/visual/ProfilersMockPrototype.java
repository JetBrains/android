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

import com.android.tools.adtui.VisualTestSeriesDataStore;
import com.android.tools.adtui.model.SeriesDataStore;

import javax.swing.*;

public class ProfilersMockPrototype {

  private static final String PROFILERS_DIALOG_TITLE = "Android Profilers";

  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      VisualTestsDialog dialog = new VisualTestsDialog();
      dialog.setTitle(PROFILERS_DIALOG_TITLE);
      dialog.addTest(new ProfilerOverviewVisualTest());
      dialog.addTest(new NetworkProfilerVisualTest());
      dialog.addTest(new CpuProfilerVisualTest());
      dialog.addTest(new MemoryProfilerVisualTest());
      dialog.pack();
      dialog.setVisible(true);
    });
    System.exit(0);
  }
}
