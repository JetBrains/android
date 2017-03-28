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

import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.NetworkTrafficFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseLineChartSegment;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

public class NetworkSegment extends BaseLineChartSegment {

  private static final String SEGMENT_NAME = "Network";

  private static final String SENDING = "Sending";

  private static final String RECEIVING = "Receiving";

  private static final String CONNECTIONS = "Connections";

  private static final BaseAxisFormatter BANDWIDTH_AXIS_FORMATTER_L1 = new NetworkTrafficFormatter(1, 5, 5); // Do not show minor ticks in L1.

  private static final BaseAxisFormatter CONNECTIONS_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 10, 1, "");

  public NetworkSegment(@NotNull Range timeCurrentRangeUs,
                        @NotNull SeriesDataStore dataStore,
                        @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, timeCurrentRangeUs, dataStore, BANDWIDTH_AXIS_FORMATTER_L1, NetworkTrafficFormatter.DEFAULT, CONNECTIONS_AXIS_FORMATTER,
          dispatcher);
  }

  @Override
  public BaseProfilerUiManager.ProfilerType getProfilerType() {
    return BaseProfilerUiManager.ProfilerType.NETWORK;
  }

  @Override
  protected void updateChartLines(boolean isExpanded) {
    // Sending and Receiving lines are present in both levels 1 and 2
    addLeftAxisLine(SeriesDataType.NETWORK_SENT, SENDING, new LineConfig(Constants.NETWORK_SENDING_COLOR));
    addLeftAxisLine(SeriesDataType.NETWORK_RECEIVED, RECEIVING, new LineConfig(Constants.NETWORK_RECEIVING_COLOR));

    if (isExpanded) {
      addRightAxisLine(SeriesDataType.NETWORK_CONNECTIONS, CONNECTIONS,
                       new LineConfig(Constants.NETWORK_CONNECTIONS_COLOR).setStepped(true));
    }
  }
}
