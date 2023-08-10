/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.cpu.adapters;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.EaseOutModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.CpuProfiler;
import com.android.tools.profilers.cpu.CpuProfilerAspect;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuThreadsModel;
import com.android.tools.profilers.cpu.CpuTraceInfo;
import com.android.tools.profilers.cpu.DetailedCpuUsage;
import com.android.tools.profilers.event.EventMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public class CpuDataProvider {

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 5, 10, "%");

  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(1, 5, 1, "");

  protected static final long PROFILING_INSTRUCTIONS_EASE_OUT_NS = TimeUnit.SECONDS.toNanos(3);
  private final StudioProfilers myProfilers;
  private final CpuThreadsModel myThreadsStates;
  private final ClampedAxisComponentModel myCpuUsageAxis;
  private final ClampedAxisComponentModel myThreadCountAxis;
  private final ResizingAxisComponentModel myTimeAxisGuide;
  private final DetailedCpuUsage myCpuUsage;
  private final CpuProfilerStage.CpuStageLegends myLegends;
  private final DurationDataModel<CpuTraceInfo> myTraceDurations;
  private final EventMonitor myEventMonitor;
  private final RangeSelectionModel myRangeSelectionModel;
  private final EaseOutModel myInstructionsEaseOutModel;

  private final CpuTraceDataSeries myCpuTraceDataSeries;

  @NotNull
  private final UpdatableManager myUpdatableManager;

  private final StreamingTimeline myTimeline;

  private final AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();

  public CpuDataProvider(@NotNull StudioProfilers profilers, @NotNull StreamingTimeline timeline) {
    myProfilers = profilers;
    myTimeline = timeline;
    Range viewRange = timeline.getViewRange();
    Range dataRange = timeline.getDataRange();
    Range selectionRange = timeline.getSelectionRange();
    myCpuUsage = new DetailedCpuUsage(profilers);
    myCpuUsageAxis = new ClampedAxisComponentModel.Builder(myCpuUsage.getCpuRange(), CPU_USAGE_FORMATTER).build();
    myThreadCountAxis = new ClampedAxisComponentModel.Builder(myCpuUsage.getThreadRange(), NUM_THREADS_AXIS).build();
    myTimeAxisGuide =
      new ResizingAxisComponentModel.Builder(viewRange, TimeAxisFormatter.DEFAULT_WITHOUT_MINOR_TICKS).setGlobalRange(dataRange).build();

    myLegends = new CpuProfilerStage.CpuStageLegends(myCpuUsage, dataRange);
    myCpuTraceDataSeries = new CpuTraceDataSeries();

    // Create an event representing the traces within the view range.
    myTraceDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, myCpuTraceDataSeries));

    myThreadsStates = new CpuThreadsModel(viewRange, profilers, myProfilers.getSession());

    myEventMonitor = new EventMonitor(profilers);

    myRangeSelectionModel = buildRangeSelectionModel(selectionRange, viewRange);

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);

    myUpdatableManager = new UpdatableManager(myProfilers.getUpdater());
  }

  public int getSelectedThread() {
    return getThreadStates().getThread();
  }

  public void setSelectedThread(int id) {
    getThreadStates().setThread(id);
    Range range = myTimeline.getSelectionRange();
    if (range.isEmpty()) {
      myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);
    }
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myAspect;
  }

  @NotNull
  public UpdatableManager getUpdatableManager() {
    return myUpdatableManager;
  }

  @NotNull
  public RangeSelectionModel getRangeSelectionModel() {
    return myRangeSelectionModel;
  }

  @NotNull
  public EaseOutModel getInstructionsEaseOutModel() {
    return myInstructionsEaseOutModel;
  }

  public AxisComponentModel getCpuUsageAxis() {
    return myCpuUsageAxis;
  }

  public AxisComponentModel getThreadCountAxis() {
    return myThreadCountAxis;
  }

  public AxisComponentModel getTimeAxisGuide() {
    return myTimeAxisGuide;
  }

  public DetailedCpuUsage getCpuUsage() {
    return myCpuUsage;
  }

  public CpuProfilerStage.CpuStageLegends getLegends() {
    return myLegends;
  }

  public DurationDataModel<CpuTraceInfo> getTraceDurations() {
    return myTraceDurations;
  }

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  @NotNull
  public CpuTraceDataSeries getCpuTraceDataSeries() {
    return myCpuTraceDataSeries;
  }

  @NotNull
  public CpuThreadsModel getThreadStates() {
    return myThreadsStates;
  }

  private RangeSelectionModel buildRangeSelectionModel(@NotNull Range selectionRange, @NotNull Range viewRange) {
    RangeSelectionModel rangeSelectionModel = new RangeSelectionModel(selectionRange, viewRange);
    rangeSelectionModel.addConstraint(myTraceDurations);
    return rangeSelectionModel;
  }

  public class CpuTraceDataSeries implements DataSeries<CpuTraceInfo> {
    @Override
    public List<SeriesData<CpuTraceInfo>> getDataForRange(Range range) {
      List<Trace.TraceInfo> traceInfos = CpuProfiler.getTraceInfoFromRange(myProfilers.getClient(), myProfilers.getSession(), range);
      List<SeriesData<CpuTraceInfo>> seriesData = new ArrayList<>();
      for (Trace.TraceInfo protoTraceInfo : traceInfos) {
        CpuTraceInfo info = new CpuTraceInfo(protoTraceInfo);
        seriesData.add(new SeriesData<>((long)info.getRange().getMin(), info));
      }
      return seriesData;
    }
  }
}
