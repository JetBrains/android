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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.CpuProfiler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ATRACE;

/**
 * This class manages thread state series data parsed from atrace captures. The data series is only responsible for
 * returning thread states supplied to it upon parsing an atrace capture.
 */
public class AtraceThreadStateDataSeries implements DataSeries<CpuProfilerStage.ThreadState> {
  @NotNull
  private final HashMap<Range, List<SeriesData<CpuProfilerStage.ThreadState>>> mySeriesData;

  public AtraceThreadStateDataSeries() {
    mySeriesData = new HashMap<>();
  }

  /**
   * @param seriesData to capture and return within a specific range.
   */
  public void addCaptureSeriesData(Range range, List<SeriesData<CpuProfilerStage.ThreadState>> seriesData) {
    if (!mySeriesData.containsKey(range)) {
      mySeriesData.put(range, seriesData);
    }
  }

  /**
   * @param overlapRange to test if there is a data series that overlaps.
   * @return the Range if one exist that overlaps the requested range.
   */
  public Range getOverlapRange(Range overlapRange) {
    for (Range range : mySeriesData.keySet()) {
      if (range.getMin() <= overlapRange.getMax() && range.getMax() > overlapRange.getMin()) {
        return range;
      }
    }
    return null;
  }

  @Override
  public List<SeriesData<CpuProfilerStage.ThreadState>> getDataForXRange(Range xRange) {
    long min = (long)xRange.getMin();
    long max = (long)xRange.getMax();
    List<SeriesData<CpuProfilerStage.ThreadState>> series = new ArrayList<>();
    Range seriesRange = getOverlapRange(xRange);
    if (seriesRange != null) {
      List<SeriesData<CpuProfilerStage.ThreadState>> seriesDataList = mySeriesData.get(seriesRange);
      if (seriesDataList.isEmpty()) {
        return series;
      }
      for (SeriesData<CpuProfilerStage.ThreadState> data : seriesDataList) {
        if (data.x >= min && data.x < max) {
          series.add(data);
        }
      }
    }
    return series;
  }
}
