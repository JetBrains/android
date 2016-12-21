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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.NetworkTrafficFormatter;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class NetworkMonitor extends ProfilerMonitor {

  private static final BaseAxisFormatter BANDWIDTH_AXIS_FORMATTER_L1 = new NetworkTrafficFormatter(1, 2, 5);
  private final NetworkUsage myNetworkUsage;
  private final LegendComponentModel myLegends;
  private final AxisComponentModel myTrafficAxis;

  public NetworkMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);

    Range dataRange = getTimeline().getDataRange();

    myNetworkUsage = new NetworkUsage(profilers);

    myTrafficAxis = new AxisComponentModel(myNetworkUsage.getTrafficRange(), BANDWIDTH_AXIS_FORMATTER_L1);
    myTrafficAxis.clampToMajorTicks(true);

    myLegends = new LegendComponentModel(100);
    myLegends.add(new LegendData(myNetworkUsage.getRxSeries(), BANDWIDTH_AXIS_FORMATTER_L1, dataRange));
    myLegends.add(new LegendData(myNetworkUsage.getTxSeries(), BANDWIDTH_AXIS_FORMATTER_L1, dataRange));
  }

  @NotNull
  public NetworkTrafficDataSeries getSpeedSeries(NetworkTrafficDataSeries.Type trafficType) {
    NetworkServiceGrpc.NetworkServiceBlockingStub client = myProfilers.getClient().getNetworkClient();
    return new NetworkTrafficDataSeries(client, myProfilers.getProcessId(), trafficType);
  }

  @NotNull
  public NetworkOpenConnectionsDataSeries getOpenConnectionsSeries() {
    NetworkServiceGrpc.NetworkServiceBlockingStub client = myProfilers.getClient().getNetworkClient();
    return new NetworkOpenConnectionsDataSeries(client, myProfilers.getProcessId());
  }

  @NotNull
  @Override
  public String getName() {
    return "Network";
  }

  @Override
  public void exit() {
    myProfilers.getUpdater().unregister(myNetworkUsage);
    myProfilers.getUpdater().unregister(myTrafficAxis);
    myProfilers.getUpdater().unregister(myLegends);
  }

  @Override
  public void enter() {
    myProfilers.getUpdater().register(myNetworkUsage);
    myProfilers.getUpdater().register(myTrafficAxis);
    myProfilers.getUpdater().register(myLegends);
  }

  public void expand() {
    myProfilers.setStage(new NetworkProfilerStage(myProfilers));
  }

  public AxisComponentModel getTrafficAxis() {
    return myTrafficAxis;
  }

  public NetworkUsage getNetworkUsage() {
    return myNetworkUsage;
  }

  public LegendComponentModel getLegends() {
    return myLegends;
  }
}
