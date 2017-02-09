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

import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class NetworkUsage extends LineChartModel {

  @NotNull private final RangedContinuousSeries myRxSeries;
  @NotNull private final RangedContinuousSeries myTxSeries;
  @NotNull private final Range myTrafficRange;

  public NetworkUsage(@NotNull StudioProfilers profilers) {

    Range viewRange = profilers.getTimeline().getViewRange();

    // We use 4 as a reasonable initial default for number of connections.
    myTrafficRange = new Range(0, 4);

    myRxSeries = new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED.getLabel(false),
                                            viewRange,
                                            myTrafficRange,
                                            createSeries(profilers, NetworkTrafficDataSeries.Type.BYTES_RECEIVED));
    myTxSeries = new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_SENT.getLabel(false),
                                            viewRange,
                                            myTrafficRange,
                                            createSeries(profilers, NetworkTrafficDataSeries.Type.BYTES_SENT));

    add(myRxSeries);
    add(myTxSeries);
  }

  @NotNull
  public NetworkTrafficDataSeries createSeries(@NotNull StudioProfilers profilers, @NotNull NetworkTrafficDataSeries.Type trafficType) {
    NetworkServiceGrpc.NetworkServiceBlockingStub client = profilers.getClient().getNetworkClient();
    return new NetworkTrafficDataSeries(client, profilers.getProcessId(), profilers.getSession(), trafficType);
  }

  @NotNull
  public RangedContinuousSeries getRxSeries() {
    return myRxSeries;
  }

  @NotNull
  public RangedContinuousSeries getTxSeries() {
    return myTxSeries;
  }

  @NotNull
  public Range getTrafficRange() {
    return myTrafficRange;
  }
}
