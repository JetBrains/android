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
package com.android.tools.adtui.segment;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.*;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class CpuUsageSegment extends BaseLineChartSegment {

  private static final String SEGMENT_NAME = "CPU";

  private static final String MY_PROCESS_SERIES_LABEL = "My Process";

  private static final String OTHER_PROCESSES_SERIES_LABEL = "Other Processes";

  private static final String THREADS_SERIES_LABEL = "Threads";

  private static final SingleUnitAxisFormatter CPU_USAGE_AXIS = new SingleUnitAxisFormatter(10, 10, 10, "%");

  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(5, 10, 1, "");

  // TODO: Set proper darcula color
  private static final Color MY_PROCESS_LINE_COLOR = new JBColor(0x85c490, 0x85c490);

  // TODO: Set proper darcula color
  private static final Color OTHER_PROCESSES_LINE_COLOR = new JBColor(0xc9d8e1, 0xc9d8e1);

  // TODO: Set proper darcula color
  private static final Color THREADS_LINE_COLOR = new JBColor(0x5a9240, 0x5a9240);

  @NotNull
  private final Range mTimeRange;

  /**
   * Creates a segment to display CPU usage information. If {@code numThreadsData} is not null, we also display the right axis, which
   * correspond to the number of live threads.
   */
  public CpuUsageSegment(@NotNull Range timeRange, @NotNull SeriesDataStore dataStore) {
    super(SEGMENT_NAME, timeRange, dataStore, CPU_USAGE_AXIS, NUM_THREADS_AXIS, new Range(0, 100), null);
    mTimeRange = timeRange;
  }

  @Override
  public void populateSeriesData(@NotNull LineChart lineChart) {
    ContinuousSeries myProcessSeries = new DataStoreContinuousSeries(mSeriesDataStore, SeriesDataType.CPU_MY_PROCESS);
    lineChart.addLine(
      new RangedContinuousSeries(MY_PROCESS_SERIES_LABEL, mTimeRange, mLeftAxisRange, myProcessSeries),
      getProcessLineConfig(MY_PROCESS_LINE_COLOR));
    ContinuousSeries otherProcessSeries = new DataStoreContinuousSeries(mSeriesDataStore, SeriesDataType.CPU_OTHER_PROCESSES);
    lineChart.addLine(
      new RangedContinuousSeries(OTHER_PROCESSES_SERIES_LABEL, mTimeRange, mLeftAxisRange, otherProcessSeries),
      getProcessLineConfig(OTHER_PROCESSES_LINE_COLOR));

    // TODO we need a way to disable data collection / enumeration for various states of a segment.
    LineConfig threadsLineConfig = new LineConfig(THREADS_LINE_COLOR);
    threadsLineConfig.setStepped(true);
    ContinuousSeries threadsSeries = new DataStoreContinuousSeries(mSeriesDataStore, SeriesDataType.CPU_THREADS);
    lineChart
      .addLine(new RangedContinuousSeries(THREADS_SERIES_LABEL, mTimeRange, mRightAxisRange, threadsSeries),
               threadsLineConfig);
  }

  private static LineConfig getProcessLineConfig(Color lineColor) {
    LineConfig lineConfig = new LineConfig(lineColor);
    lineConfig.setFilled(true);
    lineConfig.setStacked(true);
    return lineConfig;
  }
}
