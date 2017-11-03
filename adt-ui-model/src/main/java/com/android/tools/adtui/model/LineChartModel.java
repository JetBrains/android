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
    Map<Range, Double> max = new HashMap<>();
    // TODO Handle stacked configs
    for (RangedContinuousSeries ranged : mySeries) {
      Range range = ranged.getYRange();
      double yMax = Double.MIN_VALUE;

      List<SeriesData<Long>> seriesList = ranged.getSeries();
      for (int i = 0; i < seriesList.size(); i++) {
        double value = seriesList.get(i).value;
        if (yMax < value) {
          yMax = value;
        }
      }

      Double m = max.get(range);
      max.put(range, m == null ? yMax : Math.max(yMax, m));
    }

    for (Map.Entry<Range, Double> entry : max.entrySet()) {
      Range range = entry.getKey();
      // Prevent the LineChart to update the range below its current max.
      if (range.getMax() < entry.getValue()) {
        float fraction = myFirstUpdate ? 1f : Updater.DEFAULT_LERP_FRACTION;
        range.setMax(Updater.lerp(range.getMax(), entry.getValue(), fraction, elapsedNs,
                                  (float)(entry.getValue() * Updater.DEFAULT_LERP_THRESHOLD_PERCENTAGE)));
      }
    }

    myFirstUpdate = false;

    // TODO: Depend on the other things
    changed(Aspect.LINE_CHART);
  }

  public void addAll(List<RangedContinuousSeries> series) {
    series.forEach(this::add);
  }

  public void add(RangedContinuousSeries series) {
    mySeries.add(series);
  }

  public void remove(RangedContinuousSeries series) {
    mySeries.remove(series);
  }

  @NotNull
  public List<RangedContinuousSeries> getSeries() {
    return mySeries;
  }
}
