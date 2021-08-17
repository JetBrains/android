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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import org.jetbrains.annotations.NotNull;

public class NetworkUsage extends LineChartModel {

  @NotNull private final RangedContinuousSeries myRxSeries;
  @NotNull private final RangedContinuousSeries myTxSeries;
  @NotNull private final Range myTrafficRange;

  public NetworkUsage(@NotNull StudioProfilers profilers) {
    super(profilers.getIdeServices().getPoolExecutor());
    Range viewRange = profilers.getTimeline().getViewRange();
    Range dataRange = profilers.getTimeline().getDataRange();

    // We use 4 as a reasonable initial default for number of connections.
    myTrafficRange = new Range(0, 4);

    myRxSeries = new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED.getLabel(false),
                                            viewRange,
                                            myTrafficRange,
                                            createSeries(profilers, NetworkTrafficDataSeries.Type.BYTES_RECEIVED),
                                            dataRange);
    myTxSeries = new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_SENT.getLabel(false),
                                            viewRange,
                                            myTrafficRange,
                                            createSeries(profilers, NetworkTrafficDataSeries.Type.BYTES_SENT),
                                            dataRange);

    add(myRxSeries);
    add(myTxSeries);
  }

  @NotNull
  public DataSeries<Long> createSeries(@NotNull StudioProfilers profilers, @NotNull NetworkTrafficDataSeries.Type trafficType) {
    if (profilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      return new UnifiedEventDataSeries<>(profilers.getClient().getTransportClient(),
                                          profilers.getSession().getStreamId(),
                                          profilers.getSession().getPid(),
                                          Common.Event.Kind.NETWORK_SPEED,
                                          trafficType == NetworkTrafficDataSeries.Type.BYTES_SENT
                                          ? Common.Event.EventGroupIds.NETWORK_TX_VALUE
                                          : Common.Event.EventGroupIds.NETWORK_RX_VALUE,
                                          UnifiedEventDataSeries
                                            .fromFieldToDataExtractor(event -> event.getNetworkSpeed().getThroughput()));
    }
    else {
      NetworkServiceGrpc.NetworkServiceBlockingStub client = profilers.getClient().getNetworkClient();
      return new NetworkTrafficDataSeries(client, profilers.getSession(), trafficType);
    }
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
