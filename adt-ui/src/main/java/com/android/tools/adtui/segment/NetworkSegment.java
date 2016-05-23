package com.android.tools.adtui.segment;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.*;
import org.jetbrains.annotations.NotNull;

public class NetworkSegment extends BaseLineChartSegment {

  private static final String SEGMENT_NAME = "Network";
  private static final String SENDING = "Sending";
  private static final String RECEIVING = "Receiving";
  private static final String CONNECTIONS = "Connections";

  private static final BaseAxisFormatter BANDWIDTH_AXIS_FORMATTER = MemoryAxisFormatter.DEFAULT;
  private static final BaseAxisFormatter CONNECTIONS_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 10, 1, "");

  @NotNull
  private final Range mTimeRange;

  /**
   * @param connectionsData if it is null, connections line won't displayed, thus right axis won't displayed as well.
   */
  public NetworkSegment(@NotNull Range timeRange,
                        @NotNull SeriesDataStore dataStore) {
    super(SEGMENT_NAME, timeRange, dataStore, BANDWIDTH_AXIS_FORMATTER,
          CONNECTIONS_AXIS_FORMATTER, null, null);
    mTimeRange = timeRange;
  }

  @Override
  public void populateSeriesData(@NotNull LineChart lineChart) {
    // TODO(Madiyar): set corresponding colors to the lines to match the design
    ContinuousSeries sendingSeries = new DataStoreContinuousSeries(mSeriesDataStore, SeriesDataType.NETWORK_SENDING);
    lineChart.addLine(new RangedContinuousSeries(SENDING, mTimeRange, mLeftAxisRange, sendingSeries),
                      new LineConfig(AdtUiUtils.VIVID_ORANGE));

    ContinuousSeries receivingSeries = new DataStoreContinuousSeries(mSeriesDataStore, SeriesDataType.NETWORK_RECEIVING);
    lineChart.addLine(new RangedContinuousSeries(RECEIVING, mTimeRange, mLeftAxisRange, receivingSeries),
                      new LineConfig(AdtUiUtils.STRONG_BLUE));

    //TODO Move visibility to segment config

    ContinuousSeries connectionsSeries = new DataStoreContinuousSeries(mSeriesDataStore, SeriesDataType.NETWORK_CONNECTIONS);
    lineChart.addLine(new RangedContinuousSeries(CONNECTIONS, mTimeRange, mRightAxisRange, connectionsSeries),
                      new LineConfig(AdtUiUtils.DARK_GREEN));
  }
}
