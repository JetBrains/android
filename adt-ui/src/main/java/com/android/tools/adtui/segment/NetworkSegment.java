package com.android.tools.adtui.segment;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NetworkSegment extends BaseLineChartSegment {

  private static final String SEGMENT_NAME = "Network";
  private static final String SENDING = "Sending";
  private static final String RECEIVING = "Receiving";
  private static final String CONNECTIONS = "Connections";

  @NotNull
  private final Range mTimeRange;

  @NotNull
  private final ContinuousSeries mSendingData;

  @NotNull
  private final ContinuousSeries mReceivingData;

  @Nullable
  private final ContinuousSeries mConnectionsData;

  /**
   * @param connectionsData if it is null, connections line won't displayed, thus right axis won't displayed as well.
   */
  public NetworkSegment(@NotNull Range timeRange,
                        @NotNull ContinuousSeries sendingData,
                        @NotNull ContinuousSeries receivingData,
                        @Nullable ContinuousSeries connectionsData) {
    super(SEGMENT_NAME, timeRange, MemoryAxisFormatter.DEFAULT,
          (connectionsData != null) ? MemoryAxisFormatter.DEFAULT : null);
    mTimeRange = timeRange;
    mSendingData = sendingData;
    mReceivingData = receivingData;
    mConnectionsData = connectionsData;
  }

  public NetworkSegment(@NotNull Range timeRange,
                        @NotNull ContinuousSeries sendingData,
                        @NotNull ContinuousSeries receivingData) {
    this(timeRange, sendingData, receivingData, null);
  }

  @Override
  public void populateSeriesData(@NotNull LineChart lineChart) {
    // TODO(Madiyar): set corresponding colors to the lines to match the design
    lineChart.addLine(new RangedContinuousSeries(SENDING, mTimeRange, mLeftAxisRange, mSendingData));
    lineChart.addLine(new RangedContinuousSeries(RECEIVING, mTimeRange, mLeftAxisRange, mReceivingData));

    if (mConnectionsData != null) {
      // TODO(Madiyar): set corresponding colors to the lines to match the design
      lineChart.addLine(new RangedContinuousSeries(CONNECTIONS, mTimeRange, mRightAxisRange, mConnectionsData));
    }
  }
}
