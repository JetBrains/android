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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.StudioProfilers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

  public CpuCaptureMinimapModel(@NotNull StudioProfilers profilers,
                                @NotNull CpuCapture cpuCapture,
                                @NotNull Range selectionRange) {
    myCaptureRange = cpuCapture.getRange();
    Range initialCaptureViewRange = cpuCapture.getTimeline().getViewRange();
    myCpuUsage = new CpuUsage(profilers, myCaptureRange, myCaptureRange, cpuCapture);

    // Copy capture range as view range
    myRangeSelectionModel = new RangeSelectionModel(selectionRange, new Range(myCaptureRange));
    // Confine selection to the capture range.
    myRangeSelectionModel.addConstraint(new DurationDataModel<>(new RangedSeries<>(myCaptureRange, new DataSeries<CpuTraceInfo>() {
      @Override
      public List<SeriesData<CpuTraceInfo>> getDataForRange(Range range) {
        // An ad-hoc data series with just the current capture.
        List<SeriesData<CpuTraceInfo>> seriesData = new ArrayList<>();
        if (myCaptureRange.intersectsWith(range)) {
          CpuTraceInfo traceInfo = new CpuTraceInfo(
            Trace.TraceInfo.newBuilder()
              .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myCaptureRange.getMin()))
              .setToTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myCaptureRange.getMax()))
              .build());
          seriesData.add(new SeriesData<>((long)traceInfo.getRange().getMin(), traceInfo));
        }
        return seriesData;
      }
    })));
    // Set initial selection to the entire capture range.
    // The initial capture view range is set only with perfetto traces that contain UI metadata.
    // The initial value is set in SystemTraceCpuCapture. If no metadata is available this value is set to the data range of the capture.
    myRangeSelectionModel.set(initialCaptureViewRange.getMin(), initialCaptureViewRange.getMax());
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
