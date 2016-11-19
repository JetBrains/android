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
import com.android.tools.adtui.model.SeriesData;

import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.NetworkProfiler.*;

public class NetworkRadioDataSeries implements DataSeries<NetworkRadioDataSeries.RadioState>{
  public enum RadioState{
    WIFI, IDLE, LOW, HIGH, NONE
  }

  @NotNull private final NetworkServiceGrpc.NetworkServiceBlockingStub myClient;
  private final int myProcessId;

  public NetworkRadioDataSeries(@NotNull NetworkServiceGrpc.NetworkServiceBlockingStub client, int processId) {
    myClient = client;
    myProcessId = processId;
  }

  @Override
  @NotNull
  public ImmutableList<SeriesData<RadioState>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    NetworkDataRequest.Builder dataRequestBuilder = NetworkDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setType(NetworkDataRequest.Type.CONNECTIVITY)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()));
    NetworkDataResponse response = myClient.getData(dataRequestBuilder.build());

    List<SeriesData<RadioState>> seriesData = new ArrayList<>();
    for (NetworkProfilerData data : response.getDataList()) {
      long timestampUs = TimeUnit.NANOSECONDS.toMicros(data.getBasicInfo().getEndTimestamp());

      switch (data.getConnectivityData().getDefaultNetworkType()) {
        case WIFI:
          seriesData.add(new SeriesData<>(timestampUs, RadioState.WIFI));
          break;
        case MOBILE:
          switch (data.getConnectivityData().getRadioState()) {
            case ACTIVE:
              seriesData.add(new SeriesData<>(timestampUs, RadioState.HIGH));
              break;
            case IDLE:
              seriesData.add(new SeriesData<>(timestampUs, RadioState.LOW));
              break;
            case SLEEPING:
              seriesData.add(new SeriesData<>(timestampUs, RadioState.IDLE));
              break;
            default:
              seriesData.add(new SeriesData<>(timestampUs, RadioState.NONE));
              break;
          }
          break;
        default:
          seriesData.add(new SeriesData<>(timestampUs, RadioState.NONE));
          break;
      }
    }
    return ContainerUtil.immutableList(seriesData);
  }

}
