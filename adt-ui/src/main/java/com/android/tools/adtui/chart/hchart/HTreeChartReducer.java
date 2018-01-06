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
package com.android.tools.adtui.chart.hchart;

import com.android.tools.adtui.model.HNode;
import org.jetbrains.annotations.NotNull;

import java.awt.geom.Rectangle2D;
import java.util.List;

public interface HTreeChartReducer<T> {
  /**
   * Prior to drawing, this method receives rectangles and their corresponding nodes from the {@link HTreeChart}.
   * Classes implementing the interface need to modify {@code rectangles} and {@code nodes}, so that the number of rectangles to draw is
   * reduced. When the {@link HTreeChart} is drawn using modified {@code rectangles} and {@code nodes}, it should be visually similar
   * as if it was drawn without reducing.
   *
   * {@link HTreeChart} will throw an {@link AssertionError} if the reducer does not ensure that the
   * length of {@code nodes} is the same as the length of {@code rectangles}.
   */
  void reduce(@NotNull List<Rectangle2D.Float> rectangles, @NotNull List<HNode<T, ?>> nodes);
}
