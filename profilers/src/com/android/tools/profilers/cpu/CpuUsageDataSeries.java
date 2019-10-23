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
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataResponse;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuUsageDataSeries implements DataSeries<Long> {
  @NotNull
  private CpuServiceGrpc.CpuServiceBlockingStub myClient;

  private final Common.Session mySession;
  private final Function<List<Cpu.CpuUsageData>, Stream<SeriesData<Long>>> myDataExtractor;

  public CpuUsageDataSeries(@NotNull CpuServiceGrpc.CpuServiceBlockingStub client,
                            Common.Session session,
                            Function<List<Cpu.CpuUsageData>, Stream<SeriesData<Long>>> dataExtractor) {
    myClient = client;
    mySession = session;
    myDataExtractor = dataExtractor;
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    // Get an extra padding on each side, to have a smooth rendering at the edges.
    // TODO: Change the CPU API to allow specifying this padding in the request as number of samples.
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    CpuDataRequest.Builder dataRequestBuilder = CpuDataRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()) - bufferNs)
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()) + bufferNs);
    CpuDataResponse response = myClient.getData(dataRequestBuilder.build());
    return myDataExtractor.apply(response.getDataList()).collect(Collectors.toList());
  }
}
