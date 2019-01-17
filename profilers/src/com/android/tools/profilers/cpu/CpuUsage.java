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
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class CpuUsage extends LineChartModel {
  // Cpu usage is shown as percentages (e.g. 0 - 100) and no range animation is needed.
  @NotNull private final Range myCpuRange;
  @NotNull private final RangedContinuousSeries myCpuSeries;

  public CpuUsage(@NotNull StudioProfilers profilers) {
    myCpuRange = new Range(0, 100);
    DataSeries<Long> series;
    if (profilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      series = new UnifiedEventDataSeries(
        profilers.getClient().getProfilerClient(),
        profilers.getSession(),
        Common.Event.Kind.CPU_USAGE,
        profilers.getSession().getPid(),
        events -> extractData(events.stream().map(event -> event.getCpuUsage()).collect(Collectors.toList()), false));
    }
    else {
      series =
        new CpuUsageDataSeries(profilers.getClient().getCpuClient(), profilers.getSession(), dataList -> extractData(dataList, false));
    }
    myCpuSeries = new RangedContinuousSeries(getCpuSeriesLabel(), profilers.getTimeline().getViewRange(), myCpuRange, series);
    add(myCpuSeries);
  }

  @NotNull
  public Range getCpuRange() {
    return myCpuRange;
  }

  @NotNull
  public RangedContinuousSeries getCpuSeries() {
    return myCpuSeries;
  }

  protected String getCpuSeriesLabel() {
    return "";
  }

  /**
   * Extracts CPU usage percentage data from a list of {@link Cpu.CpuUsageData}.
   */
  public static Stream<SeriesData<Long>> extractData(List<Cpu.CpuUsageData> dataList, boolean isOtherProcess) {
    return IntStream.range(0, dataList.size())
      // Start from the second CPU usage data so we can compute the difference between two adjacent ones.
      .filter(index -> index > 0)
      .mapToObj(index -> getCpuUsageData(dataList, index, isOtherProcess));
  }

  private static SeriesData<Long> getCpuUsageData(List<Cpu.CpuUsageData> dataList, int index, boolean isOtherProcess) {
    Cpu.CpuUsageData data = dataList.get(index);
    Cpu.CpuUsageData lastData = dataList.get(index - 1);
    long dataTimestamp = TimeUnit.NANOSECONDS.toMicros(data.getEndTimestamp());
    long elapsed = (data.getElapsedTimeInMillisec() - lastData.getElapsedTimeInMillisec());
    // TODO: consider using raw data instead of percentage to improve efficiency.
    double app = 100.0 * (data.getAppCpuTimeInMillisec() - lastData.getAppCpuTimeInMillisec()) / elapsed;
    double system = 100.0 * (data.getSystemCpuTimeInMillisec() - lastData.getSystemCpuTimeInMillisec()) / elapsed;

    // System and app usages are read from them device in slightly different times. That can cause app usage to be slightly higher than
    // system usage and we need to adjust our values to cover these scenarios. Also, we use iowait (time waiting for I/O to complete) when
    // calculating the total elapsed CPU time. The problem is this value is not reliable (http://man7.org/linux/man-pages/man5/proc.5.html)
    // and can actually decrease. In these case we could end up with slightly negative system and app usages. That also needs adjustment and
    // we should make sure that 0% <= appUsage <= systemUsage <= 100%
    system = Math.max(0, Math.min(system, 100.0));
    app = Math.max(0, Math.min(app, system));

    return new SeriesData<>(dataTimestamp, (long)(isOtherProcess ? system - app : app));
  }
}
