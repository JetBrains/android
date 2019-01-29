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
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DurationDataModel<E extends DurationData> extends AspectModel<DurationDataModel.Aspect> implements Updatable {

  public enum Aspect {
    DURATION_DATA
  }

  @NotNull private final RangedSeries<E> mySeries;
  @Nullable private RangedContinuousSeries myAttachedLineSeries = null;
  @Nullable private Predicate<SeriesData<E>> myAttachPredicate = null;
  @Nullable private BiPredicate<SeriesData<E>, RangedContinuousSeries> myRenderSeriesPredicate = null;
  @Nullable private Interpolatable<Long, Double> myInterpolatable = null;

  public DurationDataModel(@NotNull RangedSeries<E> series) {
    mySeries = series;
    mySeries.getXRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> changed(Aspect.DURATION_DATA));
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
  public Predicate<SeriesData<E>> getAttachPredicate() {
    return myAttachPredicate;
  }

  @Nullable
  public BiPredicate<SeriesData<E>, RangedContinuousSeries> getRenderSeriesPredicate() {
    return myRenderSeriesPredicate;
  }

  @Nullable
  public Interpolatable<Long, Double> getInterpolatable() {
    return myInterpolatable;
  }

  /**
   * @param attachedSeries    the series the DurationData should be attached to.
   * @param interpolatable    the interpolation method used if the DurationData lies between two data points of the attached series.
   */
  public void setAttachedSeries(@NotNull RangedContinuousSeries attachedSeries,
                                @NotNull Interpolatable<Long, Double> interpolatable) {
    myAttachedLineSeries = attachedSeries;
    myInterpolatable = interpolatable;
  }

  /**
   * @param attachPredicate   for each DurationData, an expression that evaluates whether it should be attached to the series (if the
   *                          attachSeries has been set).
   */
  public void setAttachPredicate(@NotNull Predicate<SeriesData<E>> attachPredicate) {
    myAttachPredicate = attachPredicate;
  }

  /**
   * @param attachPredicate   for each DurationData, an expression that evaluates whether a RangedContinousSeries should be rendered within
   *                          the duration.
   */
  public void setRenderSeriesPredicate(@NotNull BiPredicate<SeriesData<E>, RangedContinuousSeries> renderSeriesPredicate) {
    myRenderSeriesPredicate = renderSeriesPredicate;
  }

  @Override
  public void update(long elapsedNs) {
    // TODO: perhaps only update on model change
    changed(Aspect.DURATION_DATA);
  }
}
