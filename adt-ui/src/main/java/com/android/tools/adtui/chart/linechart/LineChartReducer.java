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
package com.android.tools.adtui.chart.linechart;

import com.android.tools.adtui.model.SeriesData;

import java.awt.geom.Path2D;
import java.util.List;

/**
 * This interface is used by {@link LineChart} component to be able
 * to render faster by reducing its data before drawing.
 */
public interface LineChartReducer {
  /**
   * Reduces data used to represent a line.
   * The result shouldn't affect the looking of the line when it's drawn.
   */
  List<SeriesData<Long>> reduceData(List<SeriesData<Long>> data, LineConfig config);

  /**
   * Reduces the given path in a pixel level, i.e when dimensions are available.
   * The result shouldn't affect the looking of the line when it's drawn.
   */
  Path2D reducePath(Path2D path, LineConfig config);
}
