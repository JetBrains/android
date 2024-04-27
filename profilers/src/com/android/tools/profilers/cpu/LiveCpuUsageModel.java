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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.profilers.LiveDataModel;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.adapters.CpuDataProvider;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class LiveCpuUsageModel extends LiveDataModel {
  private final List<Updatable> myUpdatable;
  private final StudioProfilers myProfilers;
  private TooltipModel myCpuUsageTooltip;
  private final CpuDataProvider myCpuDataProvider;
  private final Stage myStage;

  public LiveCpuUsageModel(@NotNull StudioProfilers profilers, @NotNull Stage stage) {
    super(profilers);
    myProfilers = profilers;
    myStage = stage;
    myCpuDataProvider = new CpuDataProvider(profilers, profilers.getTimeline());
    myUpdatable = ImmutableList.of(myCpuDataProvider.getCpuUsage());
    myCpuUsageTooltip = new CpuProfilerStageCpuUsageTooltip(myCpuDataProvider.getLegends(), myCpuDataProvider.getRangeSelectionModel(),
                                                            myCpuDataProvider.getTraceDurations());
  }

  @VisibleForTesting
  protected LiveCpuUsageModel(@NotNull StudioProfilers profilers,
                              @NotNull CpuDataProvider cpuDataProvider, @NotNull Stage stage) {
    super(profilers);
    myStage = stage;
    myProfilers = profilers;
    myCpuDataProvider = cpuDataProvider;
    myUpdatable = ImmutableList.of(myCpuDataProvider.getCpuUsage());
    myCpuUsageTooltip = new CpuProfilerStageCpuUsageTooltip(myCpuDataProvider.getLegends(), myCpuDataProvider.getRangeSelectionModel(),
                                                            myCpuDataProvider.getTraceDurations());
  }

  public Stage getStage() {
    return myStage;
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myCpuDataProvider.getAspect();
  }

  public void setAndSelectCapture(long traceId) {
    // no operation here, since there is no capture for live view.
  }

  public int getSelectedThread() {
    return myCpuDataProvider.getSelectedThread();
  }

  public void setSelectedThread(int id) {
    myCpuDataProvider.setSelectedThread(id);
  }

  @NotNull
  public RangeSelectionModel getRangeSelectionModel() {
    return myCpuDataProvider.getRangeSelectionModel();
  }

  public StudioProfilers getStudioProfilers() {
    return myProfilers;
  }

  public UpdatableManager getUpdatableManager() {
    return myCpuDataProvider.getUpdatableManager();
  }

  @NotNull
  public CpuThreadsModel getThreadStates() {
    return myCpuDataProvider.getThreadStates();
  }

  public AxisComponentModel getCpuUsageAxis() {
    return myCpuDataProvider.getCpuUsageAxis();
  }

  public AxisComponentModel getThreadCountAxis() {
    return myCpuDataProvider.getThreadCountAxis();
  }

  public AxisComponentModel getTimeAxisGuide() {
    return myCpuDataProvider.getTimeAxisGuide();
  }

  public DetailedCpuUsage getCpuUsage() {
    return myCpuDataProvider.getCpuUsage();
  }

  public CpuProfilerStage.CpuStageLegends getLegends() {
    return myCpuDataProvider.getLegends();
  }

  public DurationDataModel<CpuTraceInfo> getTraceDurations() {
    return myCpuDataProvider.getTraceDurations();
  }

  public void enter() {
    myUpdatable.forEach(updatable -> myProfilers.getUpdater().register(updatable));
  }

  @Override
  public String getName() {
    return "LIVE_CPU";
  }

  @Override
  public TooltipModel getTooltip() {
    return myCpuUsageTooltip;
  }

  public void exit() {
    myUpdatable.forEach(updatable -> myProfilers.getUpdater().unregister(updatable));
  }
}