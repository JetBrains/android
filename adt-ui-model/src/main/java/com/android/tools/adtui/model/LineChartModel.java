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
package com.android.tools.adtui.model;

import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LineChartModel extends AspectModel<LineChartModel.Aspect> implements Updatable {

  public enum Aspect {
    LINE_CHART
  }

  @NotNull
  private final List<RangedContinuousSeries> mySeries = new ArrayList<>();

  /**
   * During the first update, skip the y range interpolation and snap to the initial max value.
   */
  private boolean myFirstUpdate = true;

  @Override
  public void update(long elapsedNs) {
    Map<Range, Double> maxPerRangeObject = new HashMap<>();

    // TODO Handle stacked configs
    for (RangedContinuousSeries ranged : mySeries) {
      Range range = ranged.getYRange();
      double yMax = -Double.MAX_VALUE;

      List<SeriesData<Long>> seriesList = ranged.getSeries();
      if (seriesList.isEmpty()) {
        continue;
      }

      for (SeriesData<Long> series : seriesList) {
        double value = series.value;
        if (yMax < value) {
          yMax = value;
        }
      }

      Double rangeMax = maxPerRangeObject.get(range);
      if (rangeMax == null || yMax > rangeMax) {
        maxPerRangeObject.put(range, yMax);
      }
    }

    boolean changed = myFirstUpdate; // Always fire aspect on first update.
    for (Map.Entry<Range, Double> entry : maxPerRangeObject.entrySet()) {
      Range range = entry.getKey();
      // Prevent the LineChart to update the range below its current max.
      if (range.getMax() < entry.getValue()) {
        double max = myFirstUpdate
                     ? entry.getValue()
                     : Updater.lerp(range.getMax(), entry.getValue(), Updater.DEFAULT_LERP_FRACTION, elapsedNs,
                                    (float)(entry.getValue() * Updater.DEFAULT_LERP_THRESHOLD_PERCENTAGE));
        range.setMax(max);
        changed = true;
      }
    }

    myFirstUpdate = false;
    // TODO: Depend on the other things
    if (changed) {
      changed(Aspect.LINE_CHART);
    }
  }

  public void addAll(@NotNull List<RangedContinuousSeries> series) {
    series.forEach(this::add);
  }

  public void add(@NotNull RangedContinuousSeries series) {
    mySeries.add(series);
    series.getXRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> changed(Aspect.LINE_CHART));
  }

  public void remove(@NotNull RangedContinuousSeries series) {
    series.getXRange().removeDependencies(this);
    mySeries.remove(series);
  }

  @NotNull
  public List<RangedContinuousSeries> getSeries() {
    return mySeries;
  }
}
