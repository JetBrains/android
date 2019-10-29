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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

/**
 * Model for CPU capture minimap, which provides range selection for navigating track groups in a {@link CpuCaptureStage}.
 * <p>
 * Encapsulates a CPU usage model for the duration of a capture and a range selection model.
 */
public class CpuCaptureMinimapModel {
  @NotNull
  private final Range myCaptureRange;

  @NotNull
  private final RangeSelectionModel myRangeSelectionModel;

  @NotNull
  private final CpuUsage myCpuUsage;

  public CpuCaptureMinimapModel(@NotNull StudioProfilers profilers, @NotNull CpuCapture cpuCapture) {
    myCaptureRange = cpuCapture.getRange();
    myCpuUsage = new CpuUsage(profilers, myCaptureRange, myCaptureRange, cpuCapture);

    // Set initial selection to the entire capture range.
    myRangeSelectionModel = new RangeSelectionModel(new Range());
    myRangeSelectionModel.set(myCaptureRange.getMin(), myCaptureRange.getMax());
  }

  @NotNull
  public RangeSelectionModel getRangeSelectionModel() {
    return myRangeSelectionModel;
  }

  @NotNull
  public CpuUsage getCpuUsage() {
    return myCpuUsage;
  }

  @NotNull
  public Range getCaptureRange() {
    return myCaptureRange;
  }
}
