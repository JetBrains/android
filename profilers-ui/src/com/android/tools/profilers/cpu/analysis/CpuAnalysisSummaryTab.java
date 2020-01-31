/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis;

import com.android.tools.profilers.cpu.CpuCapture;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class CpuAnalysisSummaryTab extends CpuAnalysisTab<CpuCapture> {

  private final JPanel myPanel = new JPanel();

  public CpuAnalysisSummaryTab(@NotNull CpuAnalysisTabModel<?> model) {
    super((CpuAnalysisTabModel<CpuCapture>)model);
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    //TODO (b/138408518): Populate with summary content depending on what is selected.
    return myPanel;
  }
}