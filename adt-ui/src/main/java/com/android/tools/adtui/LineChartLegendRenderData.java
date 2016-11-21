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
package com.android.tools.adtui;

import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Render data used for displaying LineChart data. In particular, it will show the most recent data in the RangedContinuousSeries
 * based on the max value of the input range.
 */
public final class LineChartLegendRenderData extends LegendRenderData {

  @NotNull private final Range myRange;
  @NotNull private final RangedContinuousSeries mySeries;
  @NotNull private final BaseAxisFormatter myFormatter;

  public LineChartLegendRenderData(@NotNull LegendRenderData.IconType icon,
                                   @NotNull Color color,
                                   @NotNull Range range,
                                   @NotNull RangedContinuousSeries series,
                                   @NotNull BaseAxisFormatter formatter) {
    super(icon, color, series.getLabel());
    myRange = range;
    mySeries = series;
    myFormatter = formatter;
  }

  @Override
  public boolean hasData() {
    return true;
  }

  @Override
  public String getFormattedData() {
    SeriesData<Long> data = mySeries.getDataSeries().getClosestData((long)myRange.getMax());
    return data == null ? null : myFormatter.getFormattedString(mySeries.getYRange().getLength(), data.value, true);
  }
}
