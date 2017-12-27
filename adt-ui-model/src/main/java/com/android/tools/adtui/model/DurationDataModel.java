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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DurationDataModel<E extends DurationData> extends AspectModel<DurationDataModel.Aspect> implements Updatable {

  public enum Aspect {
    DURATION_DATA
  }

  @NotNull private final RangedSeries<E> mySeries;
  @Nullable private RangedContinuousSeries myAttachedLineSeries = null;
  @Nullable private Interpolatable<Long, Double> myInterpolatable = null;

  public DurationDataModel(@NotNull RangedSeries<E> series) {
    mySeries = series;
  }

  @NotNull
  public RangedSeries<E> getSeries() {
    return mySeries;
  }

  @Nullable
  public RangedContinuousSeries getAttachedSeries() {
    return myAttachedLineSeries;
  }

  @Nullable
  public Interpolatable<Long, Double> getInterpolatable() {
    return myInterpolatable;
  }

  public void setAttachedSeries(@NotNull RangedContinuousSeries attached, @NotNull Interpolatable<Long, Double> interpolatable) {
    myAttachedLineSeries = attached;
    myInterpolatable = interpolatable;
  }

  @Override
  public void update(long elapsedNs) {
    // TODO: perhaps only update on model change
    changed(Aspect.DURATION_DATA);
  }
}
