/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.chart.statechart;

import com.android.tools.adtui.model.StateChartModel;
import org.jetbrains.annotations.NotNull;

/**
 * This class is used to convert {@link StateChartModel} values to strings. When the state chart
 * is set to render in text mode sometimes using the string value is not enough. When constructing
 * the {@link StateChart} the caller can pass in a StateChartTextConverter to define custom behavior
 * for converting from the value to a string.
 * @param <T> The type is expected to be the type passed into the {@link StateChartModel}
 */
public interface StateChartTextConverter<T> {

  /**
   * Use this function to define what String the {@link StateChart} should render.
   * @param value The value object we are currently rendering in the chart
   * @return A string representation of passed in value object. This string may be convert to ellipse before being drawn.
   */
  @NotNull
  String convertToString(@NotNull T value);
}
