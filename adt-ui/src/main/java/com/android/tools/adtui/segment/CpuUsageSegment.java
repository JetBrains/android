package com.android.tools.adtui.segment;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.*;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class CpuUsageSegment extends BaseLineChartSegment {

  private static final String SEGMENT_NAME = "CPU";

  private static final String MY_PROCESS_SERIES_LABEL = "My Process";

  private static final String OTHER_PROCESSES_SERIES_LABEL = "Other Processes";

  private static final String THREADS_SERIES_LABEL = "Threads";

  private static final SingleUnitAxisFormatter CPU_USAGE_AXIS = new SingleUnitAxisFormatter(10, 10, 10, "%");

  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(5, 10, 1, "");

  // TODO (amaurym): Set proper darcula color
  private static final Color MY_PROCESS_LINE_COLOR = new JBColor(0x85c490, 0x85c490);

  // TODO (amaurym): Set proper darcula color
  private static final Color OTHER_PROCESSES_LINE_COLOR = new JBColor(0xc9d8e1, 0xc9d8e1);

  // TODO (amaurym): Set proper darcula color
  private static final Color THREADS_LINE_COLOR = new JBColor(0x5a9240, 0x5a9240);


  @NotNull
  private final Range mTimeRange;

  /**
   * CPU Usage of the app process.
   */
  @NotNull
  private final ContinuousSeries mMyProcessData;

  /**
   * CPU Usage of other processes.
   */
  @Nullable
  private final ContinuousSeries mOtherProcessesData;

  @Nullable
  private final ContinuousSeries mNumThreadsData;

  /**
   * Creates a segment to display CPU usage information. If {@code numThreadsData} is not null, we also display the right axis, which
   * correspond to the number of live threads.
   */
  public CpuUsageSegment(@NotNull Range timeRange, @NotNull ContinuousSeries myProcessData, @Nullable ContinuousSeries otherProcessesData,
                         @Nullable ContinuousSeries numThreadsData) {
    super(SEGMENT_NAME, timeRange, CPU_USAGE_AXIS, (numThreadsData != null) ? NUM_THREADS_AXIS : null, new Range(0, 100), null);
    mTimeRange = timeRange;
    mMyProcessData = myProcessData;
    mOtherProcessesData = otherProcessesData;
    mNumThreadsData = numThreadsData;
  }

  public CpuUsageSegment(@NotNull Range timeRange,
                         @NotNull ContinuousSeries myProcessData) {
    this(timeRange, myProcessData, null, null);
  }

  @Override
  public void populateSeriesData(@NotNull LineChart lineChart) {

    lineChart.addLine(new RangedContinuousSeries(MY_PROCESS_SERIES_LABEL, mTimeRange, mLeftAxisRange, mMyProcessData),
                      getProcessLineConfig(MY_PROCESS_LINE_COLOR));

    if (mOtherProcessesData != null) {
      lineChart.addLine(new RangedContinuousSeries(OTHER_PROCESSES_SERIES_LABEL, mTimeRange, mLeftAxisRange, mOtherProcessesData),
                        getProcessLineConfig(OTHER_PROCESSES_LINE_COLOR));
    }

    if (mNumThreadsData != null) {
      LineConfig threadsLineConfig = new LineConfig(THREADS_LINE_COLOR);
      threadsLineConfig.setStepped(true);
      lineChart.addLine(new RangedContinuousSeries(THREADS_SERIES_LABEL, mTimeRange, mRightAxisRange, mNumThreadsData), threadsLineConfig);
    }
  }

  private static LineConfig getProcessLineConfig(Color lineColor) {
    LineConfig lineConfig = new LineConfig(lineColor);
    lineConfig.setFilled(true);
    lineConfig.setStacked(true);
    return lineConfig;
  }
}
