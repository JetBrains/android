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
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import org.jetbrains.annotations.NotNull;

public class DetailedNetworkUsage extends NetworkUsage {

  @NotNull private final Range myConnectionsRange;
  @NotNull private final RangedContinuousSeries myConnectionSeries;

  public DetailedNetworkUsage(@NotNull StudioProfilers profilers) {
    super(profilers);

    Range viewRange = profilers.getTimeline().getViewRange();

    myConnectionsRange = new Range(0, 5);

    myConnectionSeries = new RangedContinuousSeries("Connections",
                                                    viewRange,
                                                    myConnectionsRange,
                                                    createOpenConnectionsSeries(profilers));
    add(myConnectionSeries);
  }

  @NotNull
  private DataSeries<Long> createOpenConnectionsSeries(@NotNull StudioProfilers profilers) {
    if (profilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      ProfilerServiceGrpc.ProfilerServiceBlockingStub client = profilers.getClient().getProfilerClient();
      return new UnifiedEventDataSeries(client, profilers.getSession(), Common.Event.Kind.NETWORK_CONNECTION_COUNT,
                                        UnifiedEventDataSeries.DEFAULT_GROUP_ID,
                                        event -> (long)event.getNetworkConnections().getNumConnections());
    }
    else {
      NetworkServiceGrpc.NetworkServiceBlockingStub client = profilers.getClient().getNetworkClient();
      return new NetworkOpenConnectionsDataSeries(client, profilers.getSession());
    }
  }

  @NotNull
  public Range getConnectionsRange() {
    return myConnectionsRange;
  }

  @NotNull
  public RangedContinuousSeries getConnectionSeries() {
    return myConnectionSeries;
  }
}
