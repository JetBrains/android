/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;

/**
 * Base model for the summary tab.
 *
 * @param <T> type of the data to perform analysis on.
 */
public abstract class CpuAnalysisSummaryTabModel<T> extends CpuAnalysisTabModel<T> {
  @NotNull private final Range myCaptureRange;

  /**
   * @param captureRange range of the capture to perform analysis on. Required for normalizing view and selection ranges.
   */
  public CpuAnalysisSummaryTabModel(@NotNull Range captureRange) {
    super(Type.SUMMARY);
    myCaptureRange = captureRange;
  }

  @NotNull
  public Range getCaptureRange() {
    return myCaptureRange;
  }

  /**
   * @return a string representing the analysis data type.
   */
  @NotNull
  public abstract String getLabel();

  /**
   * @return range of the selected item for analysis.
   */
  @NotNull
  public abstract Range getSelectionRange();
}
