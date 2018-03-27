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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * This class manages {@link DataSeries} parsed from atrace captures. The data series is responsible for
 * returning data supplied to it upon parsing an atrace capture.
 */
public class AtraceDataSeries<T> extends InMemoryDataSeries<T> {
  @NotNull
  private final NotNullFunction<AtraceCpuCapture, List<SeriesData<T>>> mySeriesDataFunction;

  public AtraceDataSeries(@NotNull CpuProfilerStage stage, @NotNull NotNullFunction<AtraceCpuCapture, List<SeriesData<T>>> seriesDataFunction) {
    super(stage);
    mySeriesDataFunction = seriesDataFunction;
  }

  @Override
  protected List<SeriesData<T>> inMemoryDataList() {
    CpuCapture capture = myStage.getCapture();
    if (!(capture instanceof AtraceCpuCapture)) {
      return Collections.emptyList();
    }
    return mySeriesDataFunction.fun((AtraceCpuCapture)capture);
  }
}
