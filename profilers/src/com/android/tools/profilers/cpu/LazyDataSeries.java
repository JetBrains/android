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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.SeriesData;
import com.google.common.base.Preconditions;
import com.intellij.util.NotNullFunction;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * This class lazily fetches data from the provided {@link Supplier} in order to build an {@link InMemoryDataSeries}.
 *
 * <p>The supplier used should never return an null object.</p>
 */
public class LazyDataSeries<T> extends InMemoryDataSeries<T> {
  @NotNull
  private final Supplier<List<SeriesData<T>>> mySeriesDataSupplier;

  public LazyDataSeries(@NotNull Supplier<List<SeriesData<T>>> seriesDataSupplier) {
    Preconditions.checkArgument(seriesDataSupplier != null, "mySeriesDataSupplier can't be null");
    mySeriesDataSupplier = seriesDataSupplier;
  }

  @Override
  @NotNull
  protected List<SeriesData<T>> inMemoryDataList() {
    List<SeriesData<T>> producedList = mySeriesDataSupplier.get();
    Preconditions.checkState(producedList != null, "Supplier in LazyDataSeries can't produce null as result.");
    return producedList;
  }
}
