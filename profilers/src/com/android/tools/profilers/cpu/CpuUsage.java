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
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class CpuUsage extends LineChartModel {
  // Cpu usage is shown as percentages (e.g. 0 - 100) and no range animation is needed.
  @NotNull private final Range myCpuRange;
  @NotNull private final RangedContinuousSeries myCpuSeries;

  /**
   * Instantiates CPU usage model using profiler timeline ranges.
   */
  public CpuUsage(@NotNull StudioProfilers profilers) {
    this(profilers, profilers.getTimeline().getViewRange(), profilers.getTimeline().getDataRange(), null);
  }

  /**
   * Instantiates CPU usage model using the provided view and data ranges. If a capture is provided the cpu usage data is merged with the
   * data from the capture. Only {@link SystemTraceCpuCapture}'s currently support getting cpu usage data. If the capture is null or not a
   * {@link SystemTraceCpuCapture} cpu usage data is only queried from the datastore.
   */
  public CpuUsage(@NotNull StudioProfilers profilers, @NotNull Range viewRange, @NotNull Range dataRange, @Nullable CpuCapture cpuCapture) {
    myCpuRange = new Range(0, 100);
    DataSeries<Long> series = buildDataSeries(profilers.getClient().getTransportClient(), profilers.getSession(), cpuCapture);
    myCpuSeries = new RangedContinuousSeries(getCpuSeriesLabel(), viewRange, myCpuRange, series, dataRange);
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
   * Extracts CPU usage percentage data from a list of {@link Common.Event}.
   *
   * @return a list of SeriesData containing CPU usage percentage.
   */
  protected static List<SeriesData<Long>> extractData(List<Common.Event> dataList, boolean isOtherProcess) {
    return IntStream.range(0, dataList.size() - 1)
      // Calculate CPU usage percentage from two adjacent CPU usage data.
      .mapToObj(index -> getCpuUsageData(dataList.get(index).getCpuUsage(), dataList.get(index + 1).getCpuUsage(), isOtherProcess))
      .collect(Collectors.toList());
  }

  // TODO: make private after LegacyCpuUsageDataSeries is deprecated.
  protected static SeriesData<Long> getCpuUsageData(Cpu.CpuUsageData prevData, Cpu.CpuUsageData data, boolean isOtherProcess) {
    long dataTimestamp = TimeUnit.NANOSECONDS.toMicros(data.getEndTimestamp());
    long elapsed = (data.getElapsedTimeInMillisec() - prevData.getElapsedTimeInMillisec());
    // TODO: consider using raw data instead of percentage to improve efficiency.
    double app = 100.0 * (data.getAppCpuTimeInMillisec() - prevData.getAppCpuTimeInMillisec()) / elapsed;
    double system = 100.0 * (data.getSystemCpuTimeInMillisec() - prevData.getSystemCpuTimeInMillisec()) / elapsed;

    // System and app usages are read from them device in slightly different times. That can cause app usage to be slightly higher than
    // system usage and we need to adjust our values to cover these scenarios. Also, we use iowait (time waiting for I/O to complete) when
    // calculating the total elapsed CPU time. The problem is this value is not reliable (http://man7.org/linux/man-pages/man5/proc.5.html)
    // and can actually decrease. In these case we could end up with slightly negative system and app usages. That also needs adjustment and
    // we should make sure that 0% <= appUsage <= systemUsage <= 100%
    system = Math.max(0, Math.min(system, 100.0));
    app = Math.max(0, Math.min(app, system));

    return new SeriesData<>(dataTimestamp, (long)(isOtherProcess ? system - app : app));
  }

  @VisibleForTesting
  public static DataSeries<Long> buildDataSeries(@NotNull TransportServiceGrpc.TransportServiceBlockingStub client,
                                                 @NotNull Common.Session session,
                                                 @Nullable CpuCapture cpuCapture) {
    DataSeries<Long> series = new UnifiedEventDataSeries<>(
      client,
      session.getStreamId(),
      session.getPid(),
      Common.Event.Kind.CPU_USAGE,
      session.getPid(),
      events -> extractData(events, false));
    if (cpuCapture != null && cpuCapture.getSystemTraceData() != null) {
      series = new MergeCaptureDataSeries<>(cpuCapture, series,
                                            new LazyDataSeries<>(() -> cpuCapture.getSystemTraceData().getCpuUtilizationSeries()));
    }
    return series;
  }
}
