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
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

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
  private LazyDataSeries<T> myLazyDataSeries;

  @NotNull
  private final CpuCapture myCapture;

  public MergeCaptureDataSeries(@NotNull CpuCapture capture,
                                @NotNull DataSeries<T> dataStoreSeries,
                                @NotNull LazyDataSeries<T> traceState) {
    myCapture = capture;
    myLazyDataSeries = traceState;
    myDataStoreSeries = dataStoreSeries;
  }

  @Override
  public List<SeriesData<T>> getDataForRange(Range range) {
    double minRangeUs = range.getMin();
    double maxRangeUs = range.getMax();
    List<SeriesData<T>> seriesData = new ArrayList<>();
    if (myCapture instanceof SystemTraceCpuCapture) {
      Range traceRange = myCapture.getRange();
      if (traceRange.getMin() <= maxRangeUs && traceRange.getMax() >= minRangeUs) {
        // If our trace starts before our requested we query only for the last bit of data in our range.
        // otherwise we request all data from the start up to the first event found in atrace. This ensures we cover two scenarios.
        // [xx] is the capture range and data.
        // |--| is the requested range and DataStore data.
        // 1) We have all data up to our first data point in our trace. |----[xxx]|
        // 2) We have the last state change from before our trace in the event we have no trace data available |-[]|
        double traceStart = traceRange.getMin() < minRangeUs ? minRangeUs : traceRange.getMin();
        // Request data from our trace up to the max trace range or requested range whatever is less.
        double atraceSeriesRequestTo = traceRange.getMax();
        if (traceRange.getMax() > maxRangeUs) {
          atraceSeriesRequestTo = maxRangeUs;
        }
        // We request atrace data from our trace start up to the trace end, or request range.
        List<SeriesData<T>> atraceSeries = getDataForRangeFromSeries(traceStart, atraceSeriesRequestTo, myLazyDataSeries);

        // If we have trace data we adjust the range we request sampled data from.
        // The end point of our sampled data is either the first sampled point in our Atrace series. Or if we have no Atrace data
        // we request all other data up to the end of our trace.
        double firstAtraceSeriesEntryTime = !atraceSeries.isEmpty() ? atraceSeries.get(0).x : atraceSeriesRequestTo;
        List<SeriesData<T>> dataStoreSeries = getDataForRangeFromSeries(minRangeUs, firstAtraceSeriesEntryTime, myDataStoreSeries);

        minRangeUs = atraceSeriesRequestTo;

        // Merge our DataStore series and our trace series.
        // We find the first time from our DataStore that is less than our Atrace and add that to our results.
        // followed by all results from our trace series.
        int dataStoreSeriesIndex = dataStoreSeries.size();
        if (!atraceSeries.isEmpty()) {
          while(--dataStoreSeriesIndex >= 0 && dataStoreSeries.get(dataStoreSeriesIndex).x > atraceSeries.get(0).x);
          dataStoreSeriesIndex++;
        }
        seriesData.addAll(dataStoreSeries.subList(0, dataStoreSeriesIndex));
        seriesData.addAll(atraceSeries);
      }
    }

    // If we still have data to request, get this data and append it to our results.
    if (minRangeUs < maxRangeUs) {
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
    return series.getDataForRange(range);
  }
}
