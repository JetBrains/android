/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilerTooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Tooltip shown when user hovers mouse over the radio state area in Network Profiler stage. Shows radio state, timing information, etc.
 */
public final class NetworkRadioTooltip extends AspectModel<NetworkRadioTooltip.Aspect> implements ProfilerTooltip {

  public enum Aspect {
    // The hovering radio state changed
    RADIO_STATE,
  }

  /**
   * Radio state data to pass to the view.
   */
  public static class RadioStateData {
    /**
     * Current radio state.
     */
    @NotNull private NetworkRadioDataSeries.RadioState myRadioState;

    /**
     * Start and end time of the current radio state.
     */
    @NotNull private Range myRadioStateRange;

    public RadioStateData(@NotNull NetworkRadioDataSeries.RadioState radioState, @NotNull Range radioStateRange) {
      myRadioState = radioState;
      myRadioStateRange = radioStateRange;
    }

    @NotNull
    public NetworkRadioDataSeries.RadioState getRadioState() {
      return myRadioState;
    }

    @NotNull
    public Range getRadioStateRange() {
      return myRadioStateRange;
    }
  }

  @Nullable private RadioStateData myRadioStateData;

  @NotNull private final NetworkProfilerStage myStage;

  /**
   * Stores radio state data series so we can find the radio state and its start/end time on mouse hover.
   */
  @NotNull private RangedSeries<NetworkRadioDataSeries.RadioState> myRadioDataSeries;

  NetworkRadioTooltip(@NotNull NetworkProfilerStage stage) {
    myStage = stage;
    myRadioDataSeries = stage.getRadioState().getSeries().get(0);
    updateRadioState();

    stage.getStudioProfilers().getTimeline().getTooltipRange().addDependency(this).onChange(Range.Aspect.RANGE, this::updateRadioState);
    stage.getStudioProfilers().getTimeline().getDataRange().addDependency(this).onChange(Range.Aspect.RANGE, this::updateRadioState);
  }

  @Override
  public void dispose() {
    myStage.getStudioProfilers().getTimeline().getTooltipRange().removeDependencies(this);
    myStage.getStudioProfilers().getTimeline().getDataRange().removeDependencies(this);
  }

  private void updateRadioState() {
    ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();
    Range tooltipRange = timeline.getTooltipRange();
    if (tooltipRange.isEmpty()) {
      return;
    }

    // Unlike Events, the radio state data we get is just a series of one-dimensional states. So we need the entire data range to find the
    // beginning and end of the current radio state.
    List<SeriesData<NetworkRadioDataSeries.RadioState>> series =
      myRadioDataSeries.getDataSeries().getDataForXRange(timeline.getDataRange());

    // Find radio state at tooltip position.
    // binarySearch returns (-(insertion point)-1) if not found, in which case we want to find the largest value smaller than tooltip
    // position, which is (insertion point) - 1.
    int radioStateIndex = Collections.binarySearch(
      series,
      new SeriesData<NetworkRadioDataSeries.RadioState>((long)tooltipRange.getMin(), null), // Dummy object so we can compare.
      Comparator.comparingDouble(seriesData -> seriesData.x));
    if (radioStateIndex < 0) {
      radioStateIndex = -radioStateIndex - 2;
    }

    if (radioStateIndex >= 0) {
      myRadioStateData = new RadioStateData(series.get(radioStateIndex).value,
                                            new Range(series.get(0).x, timeline.getDataRange().getMax()));

      // Find start time of the target radio state. If already first, fall back to first data point in the series.
      for (int i = radioStateIndex - 1; i >= 0; --i) {
        if (series.get(i).value != myRadioStateData.getRadioState()) {
          myRadioStateData.getRadioStateRange().setMin(series.get(i + 1).x);
          break;
        }
      }

      // Find end time of the target radio state. If already last, fall back to end of the data range.
      for (int i = radioStateIndex + 1; i < series.size(); ++i) {
        if (series.get(i).value != myRadioStateData.getRadioState()) {
          myRadioStateData.getRadioStateRange().setMax(series.get(i).x);
          break;
        }
      }
    }
    else {
      myRadioStateData = null;
    }
    changed(Aspect.RADIO_STATE);
  }

  @Nullable
  public RadioStateData getRadioStateData() {
    return myRadioStateData;
  }
}
