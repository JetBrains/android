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
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class is a data series representing bytes in / bytes out network traffic (based on the
 * {@link Type} passed into the constructor).
 *
 * It is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class NetworkTrafficDataSeries implements DataSeries<Long> {
  public enum Type {
    BYTES_RECEIVED("Receiving", "Received") {
      @Override
      long getBytes(@NotNull NetworkProfiler.SpeedData data) {
        return data.getReceived();
      }
    },
    BYTES_SENT("Sending", "Sent") {
      @Override
      long getBytes(@NotNull NetworkProfiler.SpeedData data) {
        return data.getSent();
      }
    };

    private final String myLabel;
    private final String myTooltipLabel;

    Type(String label, String tooltipLabel) {
      myLabel = label;
      myTooltipLabel = tooltipLabel;
    }

    @NotNull
    public String getLabel(boolean tooltip) {
      return tooltip ? myTooltipLabel : myLabel;
    }

    abstract long getBytes(@NotNull NetworkProfiler.SpeedData data);
  }

  @NotNull
  private NetworkServiceGrpc.NetworkServiceBlockingStub myClient;
  private final int myProcessId;
  private final Common.Session mySession;
  private final Type myType;

  public NetworkTrafficDataSeries(@NotNull NetworkServiceGrpc.NetworkServiceBlockingStub client, int id, Common.Session session, Type type) {
    myClient = client;
    myProcessId = id;
    mySession = session;
    myType = type;
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    List<SeriesData<Long>> seriesData = new ArrayList<>();

    // TODO: Change the Network API to allow specifying padding in the request as number of samples.
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    NetworkProfiler.NetworkDataRequest.Builder dataRequestBuilder = NetworkProfiler.NetworkDataRequest.newBuilder()
      .setProcessId(myProcessId)
      .setSession(mySession)
      .setType(NetworkProfiler.NetworkDataRequest.Type.SPEED)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()) - bufferNs)
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()) + bufferNs);
    NetworkProfiler.NetworkDataResponse response = myClient.getData(dataRequestBuilder.build());
    for (NetworkProfiler.NetworkProfilerData data : response.getDataList()) {
      long xTimestamp = TimeUnit.NANOSECONDS.toMicros(data.getBasicInfo().getEndTimestamp());
      NetworkProfiler.SpeedData speedData = data.getSpeedData();
      seriesData.add(new SeriesData<>(xTimestamp, myType.getBytes(speedData)));
    }
    return seriesData;
  }
}
