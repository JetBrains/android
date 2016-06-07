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
package com.android.tools.idea.monitor.ui.network.view;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.ContinuousSeries;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.idea.monitor.datastore.DataStoreContinuousSeries;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseLineChartSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.intellij.util.EventDispatcher;
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

  public NetworkSegment(@NotNull Range timeRange,
                        @NotNull SeriesDataStore dataStore,
                        @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, timeRange, dataStore, BANDWIDTH_AXIS_FORMATTER,
          CONNECTIONS_AXIS_FORMATTER, null, null, dispatcher);
    mTimeRange = timeRange;
  }

  @Override
  public SegmentType getSegmentType() {
    return SegmentType.NETWORK;
  }

  @Override
  public void populateSeriesData(@NotNull LineChart lineChart) {
    // TODO(Madiyar): set corresponding colors to the lines to match the design
    ContinuousSeries sendingSeries = new DataStoreContinuousSeries(mSeriesDataStore, SeriesDataType.NETWORK_SENDING);
    lineChart.addLine(new RangedContinuousSeries(SENDING, mTimeRange, mLeftAxisRange, sendingSeries),
                      new LineConfig(AdtUiUtils.NETWORK_SENDING));

    ContinuousSeries receivingSeries = new DataStoreContinuousSeries(mSeriesDataStore, SeriesDataType.NETWORK_RECEIVING);
    lineChart.addLine(new RangedContinuousSeries(RECEIVING, mTimeRange, mLeftAxisRange, receivingSeries),
                      new LineConfig(AdtUiUtils.NETWORK_RECEIVING));

    //TODO Move visibility to segment config
    ContinuousSeries connectionsSeries = new DataStoreContinuousSeries(mSeriesDataStore, SeriesDataType.NETWORK_CONNECTIONS);
    LineConfig connectionsConf = new LineConfig(AdtUiUtils.NETWORK_CONNECTIONS);
    connectionsConf.setStepped(true);
    lineChart.addLine(new RangedContinuousSeries(CONNECTIONS, mTimeRange, mRightAxisRange, connectionsSeries),
                      connectionsConf);
  }
}
