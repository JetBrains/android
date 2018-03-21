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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.proto.NetworkProfiler.*;

public class NetworkRadioDataSeries implements DataSeries<NetworkRadioDataSeries.RadioState>{
  public enum RadioState{
    WIFI, LOW, HIGH, NONE
  }

  @NotNull private final NetworkServiceGrpc.NetworkServiceBlockingStub myClient;
  @NotNull private final Common.Session mySession;

  public NetworkRadioDataSeries(@NotNull NetworkServiceGrpc.NetworkServiceBlockingStub client, @NotNull Common.Session session) {
    myClient = client;
    mySession = session;
  }

  @Override
  @NotNull
  public List<SeriesData<RadioState>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    // TODO: Change the Network API to allow specifying padding in the request as number of samples.
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    NetworkDataRequest.Builder dataRequestBuilder = NetworkDataRequest.newBuilder()
      .setSession(mySession)
      .setType(NetworkDataRequest.Type.CONNECTIVITY)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()) - bufferNs)
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()) + bufferNs);
    NetworkDataResponse response = myClient.getData(dataRequestBuilder.build());

    List<SeriesData<RadioState>> seriesData = new ArrayList<>();
    for (NetworkProfilerData data : response.getDataList()) {
      long timestampUs = TimeUnit.NANOSECONDS.toMicros(data.getEndTimestamp());

      switch (data.getConnectivityData().getDefaultNetworkType()) {
        case WIFI:
          seriesData.add(new SeriesData<>(timestampUs, RadioState.WIFI));
          break;
        case MOBILE:
          switch (data.getConnectivityData().getRadioState()) {
            case HIGH:
              seriesData.add(new SeriesData<>(timestampUs, RadioState.HIGH));
              break;
            case LOW:
              seriesData.add(new SeriesData<>(timestampUs, RadioState.LOW));
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
    return seriesData;
  }

}
