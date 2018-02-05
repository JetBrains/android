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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * This class combines {@link ThreadStateDataSeries} and {@link AtraceThreadStateDataSeries}.
 * If the {@link AtraceThreadStateDataSeries} contains series data for the request range,
 * its results are returned otherwise {@link ThreadStateDataSeries} results are used.
 * The {@link AtraceThreadStateDataSeries} is populated when an Atrace capture is parsed.
 */
public class MergeCaptureDataSeries<T> implements DataSeries<T> {
  // The {@link DataStore} data series that contains sampled data pulled from perfd. This series
  // is used when the request range does not overlap the Atrace data series.
  @NotNull
  private final DataSeries<T> myDataStoreSeries;

  // The Atrace data series will contain thread state when an Atrace capture is parsed.
  // The series overrides any thread state data coming from perfd. As the Atrace capture
  // has more accurate data.
  @NotNull
  private AtraceDataSeries<T> myAtraceDataSeries;

  @NotNull
  private final CpuProfilerStage myStage;

  public MergeCaptureDataSeries(@NotNull CpuProfilerStage stage,
                                @NotNull DataSeries<T> dataStoreSeries,
                                @NotNull AtraceDataSeries traceState) {
    myStage = stage;
    myAtraceDataSeries = traceState;
    myDataStoreSeries = dataStoreSeries;
  }

  @Override
  public List<SeriesData<T>> getDataForXRange(Range xRange) {
    double minRangeUs = xRange.getMin();
    double maxRangeUs = xRange.getMax();
    List<SeriesData<T>> seriesData = new ArrayList<>();
    if (myStage.getCapture() instanceof AtraceCpuCapture) {
      Range traceRange = myStage.getCapture().getRange();
      if (traceRange.getMin() <= maxRangeUs && traceRange.getMax() >= minRangeUs) {
        // If the trace starts before our minimum requested range capture we pull all data from the trace.
        // [##] is the capture range and data.
        // |--| is the requested range and DataStore data.
        // [###|####]--------| or [##|###########|##]
        if (traceRange.getMin() <= minRangeUs) {
          double requestTo = traceRange.getMax();
          if (traceRange.getMax() > maxRangeUs) {
            requestTo = maxRangeUs;
          }
          seriesData.addAll(getDataForRangeFromSeries(minRangeUs, requestTo, myAtraceDataSeries));
          minRangeUs = traceRange.getMax();
        }
        // If our trace starts before our max requested range and extends beyond it.
        // |------[#####|####]
        else if (traceRange.getMax() > maxRangeUs) {
          seriesData.addAll(getDataForRangeFromSeries(
            traceRange.getMin(), maxRangeUs, myAtraceDataSeries));
          minRangeUs = maxRangeUs;
        }
        // Our trace starts somewhere in the middle so we store a request for both Datastore data and capture data.
        // |----[####]----|
        else {
          seriesData.addAll(getDataForRangeFromSeries(
            minRangeUs, traceRange.getMin(), myDataStoreSeries));
          seriesData
            .addAll(getDataForRangeFromSeries(traceRange.getMin(), traceRange.getMax(), myAtraceDataSeries));
          minRangeUs = traceRange.getMax();
        }
      }
    }

    if (minRangeUs != maxRangeUs) {
      seriesData.addAll(getDataForRangeFromSeries(minRangeUs, maxRangeUs, myDataStoreSeries));
    }

    return seriesData;
  }

  /**
   * @param minUs to use as the min time for our range.
   * @param maxUs to use as the max time for our range.
   * @param series series to query data from.
   * @return results from data series query.
   */
  private List<SeriesData<T>> getDataForRangeFromSeries(
    double minUs, double maxUs, DataSeries<T> series) {
    Range range = new Range(minUs, maxUs);
    return series.getDataForXRange(range);
  }
}
