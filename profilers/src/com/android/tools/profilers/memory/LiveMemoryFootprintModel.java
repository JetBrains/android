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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.profilers.LiveDataModel;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.MemoryDataProvider;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class LiveMemoryFootprintModel extends LiveDataModel {
  private final List<Updatable> myUpdatables;
  private final StudioProfilers myProfilers;
  private final MemoryUsageTooltip myMemoryUsageTooltip;
  private final MemoryDataProvider myMemoryDataProvider;

  public LiveMemoryFootprintModel(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProfilers = profilers;
    myMemoryDataProvider = new MemoryDataProvider(profilers, profilers.getTimeline());
    myUpdatables = ImmutableList.of(myMemoryDataProvider.getDetailedMemoryUsage());
    myMemoryUsageTooltip = new MemoryUsageTooltip(getTooltipLegends(),
                                                  isLiveAllocationTrackingReady());
  }

  @VisibleForTesting
  LiveMemoryFootprintModel(@NotNull StudioProfilers profilers,
                           @NotNull MemoryDataProvider memoryDataProvider) {
    super(profilers);
    myProfilers = profilers;
    myMemoryDataProvider = memoryDataProvider;
    myUpdatables = ImmutableList.of(myMemoryDataProvider.getDetailedMemoryUsage());
    myMemoryUsageTooltip = new MemoryUsageTooltip(getTooltipLegends(),
                                                  isLiveAllocationTrackingReady());
  }

  public MemoryDataProvider getMemoryDataProvider() {
    return myMemoryDataProvider;
  }

  public StudioProfilers getStudioProfilers() {
    return myProfilers;
  }

  public DetailedMemoryUsage getDetailedMemoryUsage() {
    return myMemoryDataProvider.getDetailedMemoryUsage();
  }

  public MemoryStageLegends getLegends() {
    return myMemoryDataProvider.getLegends();
  }

  public ClampedAxisComponentModel getMemoryAxis() {
    return myMemoryDataProvider.getMemoryAxis();
  }

  public ClampedAxisComponentModel getObjectAxis() {
    return myMemoryDataProvider.getObjectsAxis();
  }

  public Boolean isLiveAllocationTrackingReady() {
    return myMemoryDataProvider.isLiveAllocationTrackingReady();
  }

  public MemoryStageLegends getTooltipLegends() {
    return myMemoryDataProvider.getTooltipLegends();
  }

  public RangeSelectionModel getRangeSelectionModel() {
    return new RangeSelectionModel(getTimeline().getSelectionRange(),
                                   getTimeline().getViewRange());
  }

  public void enter() {
    myUpdatables.forEach(updatable -> myProfilers.getUpdater().register(updatable));
  }

  @Override
  public String getName() {
    return "LIVE_MEMORY";
  }

  @Override
  public TooltipModel getTooltip() {
    return myMemoryUsageTooltip;
  }

  public void exit() {
    myUpdatables.forEach(updatable -> myProfilers.getUpdater().unregister(updatable));
  }
}