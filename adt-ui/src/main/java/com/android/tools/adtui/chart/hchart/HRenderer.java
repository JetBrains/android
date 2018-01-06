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

import java.awt.*;
import java.awt.geom.Rectangle2D;

public interface HRenderer<T> {
  /**
   * Render a target {@link HNode}, fitting it into the specified {@code drawingArea}.
   *
   * @param isFocused If true, consider altering the node's color somehow to set it apart from other nodes in this chart.
   */
  void render(@NotNull Graphics2D g, @NotNull HNode<T, ?> node, @NotNull Rectangle2D drawingArea, boolean isFocused);
}
