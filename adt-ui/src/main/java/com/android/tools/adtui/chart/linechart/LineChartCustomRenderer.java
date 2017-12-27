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
package com.android.tools.adtui.chart.linechart;

import com.android.tools.adtui.model.RangedContinuousSeries;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * A interface to support adding custom renderers to {@link LineChart} to modify how line series are drawn.
 * e.g. changing the line colors to be different from the existing {@link LineConfig} or greying out specific regions.
 *
 * See {@link DurationDataRenderer} for example use cases.
 */
public interface LineChartCustomRenderer {

  void renderLines(@NotNull LineChart lineChart,
                   @NotNull Graphics2D g2d,
                   @NotNull List<Path2D> transformedPaths,
                   @NotNull List<RangedContinuousSeries> transformedSeries);
}
