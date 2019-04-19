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
import com.android.tools.profiler.proto.Energy;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyRequest;
import com.android.tools.profilers.ProfilerClient;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class EnergyUsageDataSeries implements DataSeries<Long> {

  @NotNull private final ProfilerClient myClient;
  private final Common.Session mySession;
  @NotNull private final Function<Energy.EnergyUsageData, Integer> myUsageExtractor;

  protected static int getTotalUsage(@NotNull Energy.EnergyUsageData usage) {
    return usage.getCpuUsage() + usage.getNetworkUsage() + usage.getLocationUsage();
  }

  /**
   * Construct a data series which adds up all the different sources of energy in any given {@link Energy.EnergyUsageData}
   */
  public EnergyUsageDataSeries(@NotNull ProfilerClient client, Common.Session session) {
    this(client, session, EnergyUsageDataSeries::getTotalUsage);
  }

  EnergyUsageDataSeries(@NotNull ProfilerClient client,
                        Common.Session session,
                        @NotNull Function<Energy.EnergyUsageData, Integer> usageExtractor) {
    myClient = client;
    mySession = session;
    myUsageExtractor = usageExtractor;
  }

  @Override
  public List<SeriesData<Long>> getDataForXRange(Range range) {
    EnergyRequest.Builder builder = EnergyRequest.newBuilder().setSession(mySession);
    long bufferNs = TimeUnit.SECONDS.toNanos(1);
    builder.setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long) range.getMin()) - bufferNs);
    builder.setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long) range.getMax()) + bufferNs);
    EnergyProfiler.EnergySamplesResponse samples = myClient.getEnergyClient().getSamples(builder.build());

    return samples.getSamplesList().stream()
      .map(
        data -> new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(data.getTimestamp()), (long)myUsageExtractor.apply(data.getEnergyUsage())))
      .collect(Collectors.toList());
  }
}
