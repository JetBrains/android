package com.android.tools.adtui.segment;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUIUtils;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.*;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NetworkSegment extends BaseLineChartSegment {

  private static final String SEGMENT_NAME = "Network";

  private static final String SENDING = "Sending";
  private static final String RECEIVING = "Receiving";
  private static final String CONNECTIONS = "Connections";

  private static final BaseAxisFormatter BANDWIDTH_AXIS_FORMATTER = MemoryAxisFormatter.DEFAULT;
  private static final BaseAxisFormatter CONNECTIONS_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 10, 1, "");

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
    super(SEGMENT_NAME, timeRange, BANDWIDTH_AXIS_FORMATTER, (connectionsData != null) ? CONNECTIONS_AXIS_FORMATTER : null);
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
    RangedContinuousSeries sendingLine = new RangedContinuousSeries(SENDING, mTimeRange, mLeftAxisRange, mSendingData);
    lineChart.addLine(sendingLine, new LineConfig(AdtUIUtils.VIVID_ORANGE));

    RangedContinuousSeries receivingLine = new RangedContinuousSeries(RECEIVING, mTimeRange, mLeftAxisRange, mReceivingData);
    lineChart.addLine(receivingLine, new LineConfig(AdtUIUtils.STRONG_BLUE));

    if (mConnectionsData != null) {
      RangedContinuousSeries connectionsLine = new RangedContinuousSeries(CONNECTIONS, mTimeRange, mRightAxisRange, mConnectionsData);
      LineConfig connectionsLineConfig = new LineConfig(AdtUIUtils.DARK_GREEN);
      connectionsLineConfig.setStepped(true);
      lineChart.addLine(connectionsLine, connectionsLineConfig);
    }
  }
}
