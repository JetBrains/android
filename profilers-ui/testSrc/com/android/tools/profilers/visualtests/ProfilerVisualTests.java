/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profilers.visualtests;

import com.android.tools.adtui.visualtests.VisualTestsDialog;
import javax.swing.SwingUtilities;

public final class ProfilerVisualTests {
  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        VisualTestsDialog dialog = new VisualTestsDialog();
        dialog.addTest(new CpuHTreeChartReducerVisualTest());
        dialog.addTest(new CaptureNodeModelRendererVisualTest());
        dialog.setTitle("Visual Tests");
        dialog.pack();
        dialog.setVisible(true);
    });
    System.exit(0);
  }
}
