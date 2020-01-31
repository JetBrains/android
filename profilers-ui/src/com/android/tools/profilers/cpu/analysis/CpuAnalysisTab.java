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

import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * The view component for {@link CpuAnalysisTabModel}. Each model can have its own unique data type as such its is the responsibility
 * of the child view type to do proper type checking. An example of a child tab is the {@link CpuAnalysisSummaryTab}.
 */
public abstract class CpuAnalysisTab<T> {
  public static CpuAnalysisTab create(@NotNull CpuAnalysisTabModel<?> model) {
    switch (model.getType()) {
      case SUMMARY:
        return new CpuAnalysisSummaryTab(model);
      default:
        throw new IllegalArgumentException("The supplied type " + model.getType() + " does not have an associated tab view.");
    }
  }

  private final CpuAnalysisTabModel<T> myModel;

  public CpuAnalysisTab(@NotNull CpuAnalysisTabModel<T> model) {
    myModel = model;
  }

  @NotNull
  public CpuAnalysisTabModel<T> getModel() {
    return myModel;
  }

  /**
   * The result of this function is returned as the body of the tab.
   */
  @NotNull
  protected abstract JComponent getComponent();
}
