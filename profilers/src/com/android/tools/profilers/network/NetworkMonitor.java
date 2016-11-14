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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class NetworkMonitor extends ProfilerMonitor {

  private final int myProcessId;

  @NotNull
  private final StudioProfilers myProfilers;

  private final Range myYRange = new Range();

  public NetworkMonitor(@NotNull StudioProfilers profilers, int pid) {
    myProcessId = pid;
    myProfilers = profilers;
  }

  @NotNull
  public Range getYRange() {
    return myYRange;
  }

  @NotNull
  public RangedContinuousSeries getSpeedSeries(NetworkTrafficDataSeries.Type trafficType) {
    NetworkServiceGrpc.NetworkServiceBlockingStub client = myProfilers.getClient().getNetworkClient();

    NetworkTrafficDataSeries series = new NetworkTrafficDataSeries(client, myProcessId, trafficType);
    return new RangedContinuousSeries(trafficType.getLabel(), myProfilers.getViewRange(), myYRange, series);
  }

  @NotNull
  @Override
  public String getName() {
    return "Network";
  }

  public void expand() {
    myProfilers.setStage(new NetworkProfilerStage(myProfilers));
  }
}
