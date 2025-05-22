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

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class StateChartModel<E> extends AspectModel<StateChartModel.Aspect> {

  public enum Aspect {
    MODEL_CHANGED
  }

  @NotNull
  private final List<RangedSeries<E>> mSeriesList;

  public StateChartModel() {
    mSeriesList = new ArrayList<>();
  }

  public List<RangedSeries<E>> getSeries() {
    return mSeriesList;
  }

  public void addSeries(@NotNull RangedSeries<E> series) {
    mSeriesList.add(series);
    series.getXRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> changed(Aspect.MODEL_CHANGED));
    changed(Aspect.MODEL_CHANGED);
  }
}
