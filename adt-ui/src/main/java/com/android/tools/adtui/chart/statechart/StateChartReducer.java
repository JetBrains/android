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
package com.android.tools.adtui.chart.statechart;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * This interface is used by {@link StateChart} to reduce its rectangles before drawing to achieve better performance.
 */
public interface StateChartReducer<T> {
  /**
   * Prior to drawing, this method receives rectangular shapes of states and their corresponding values from the {@link StateChart}.
   * Classes implementing the interface need to modify {@code rectangles} and {@code values}, so that the number of states to draw is
   * reduced. When the {@link StateChart} is drawn using modified {@code rectangles} and {@code values}, it should be visually similar
   * as if it was drawn without reducing.
   *
   * {@link StateChart} will throw an {@link AssertionError} if the reducer does not ensure that the
   * length of {@code values} is the same as the length of {@code rectangles}.
   */
  void reduce(@NotNull List<Shape> rectangles,
              @NotNull List<T> values);
}
