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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataResponse;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;

/**
 * Legacy class responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class LegacyCpuUsageDataSeries implements DataSeries<Long> {
  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub myClient;

  private final Common.Session mySession;
  private final boolean myIsOtherProcess;

  public LegacyCpuUsageDataSeries(@NotNull CpuServiceGrpc.CpuServiceBlockingStub client,
                                  Common.Session session,
                                  boolean isOtherProcess) {
    myClient = client;
    mySession = session;
    myIsOtherProcess = isOtherProcess;
  }

  @Override
  public List<SeriesData<Long>> getDataForRange(@NotNull Range timeCurrentRangeUs) {
    // Get an extra padding on each side, to have a smooth rendering at the edges.
    // TODO: Change the CPU API to allow specifying this padding in the request as number of samples.
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    CpuDataRequest.Builder dataRequestBuilder = CpuDataRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()) - bufferNs)
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()) + bufferNs);
    CpuDataResponse response = myClient.getData(dataRequestBuilder.build());
    return IntStream.range(0, response.getDataList().size() - 1)
      // Calculate CPU usage percentage from two adjacent CPU usage data.
      .mapToObj(index -> CpuUsage.getCpuUsageData(response.getData(index), response.getData(index + 1), myIsOtherProcess))
      .collect(Collectors.toList());
  }
}
