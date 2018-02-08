// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyDataResponse;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyRequest;
import com.android.tools.profiler.proto.EnergyProfiler.EnergySample;
import com.android.tools.profilers.ProfilerClient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

class EnergyUsageDataSeries implements DataSeries<Long> {

  @NotNull private final ProfilerClient myClient;
  private final Common.Session mySession;
  @NotNull private final Function<EnergySample, Integer> mySampleToUsage;

  private static int getTotalUsage(@NotNull EnergySample sample) {
    return sample.getCpuUsage() + sample.getNetworkUsage();
  }

  /**
   * Construct a data series which adds up all the different sources of energy in any given {@link EnergySample}
   */
  EnergyUsageDataSeries(@NotNull ProfilerClient client, Common.Session session) {
    this(client, session, EnergyUsageDataSeries::getTotalUsage);
  }

  EnergyUsageDataSeries(@NotNull ProfilerClient client, Common.Session session, @NotNull Function<EnergySample, Integer> sampleToUsage) {
    myClient = client;
    mySession = session;
    mySampleToUsage = sampleToUsage;
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(Range range) {
    EnergyRequest.Builder builder = EnergyRequest.newBuilder().setSession(mySession);
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    builder.setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long) range.getMin()) - bufferNs);
    builder.setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long) range.getMax()) + bufferNs);
    EnergyDataResponse energyData = myClient.getEnergyClient().getData(builder.build());

    return energyData.getSampleDataList().stream()
      .map(data -> new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(data.getTimestamp()), (long)mySampleToUsage.apply(data)))
      .collect(Collectors.toList());
  }
}
