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

/**
 * This class holds configuration values for the StateChart component.
 *
 * @param <E> The type of data represented by the state chart.
 */
public class StateChartConfig<E extends Enum<E>> {

  /**
   * Reducer used to minimize the number of points drawn.
   */
  private @NotNull StateChartReducer<E> myReducer;

  /**
   * Height value as a percentage {0...1} that represents the height of rectangles created by the state chart.
   */
  private double myRectangleHeightRatio;

  /**
   * Height value as a percentage {0...1} that represents the height of rectangles while the mouse is hovering over them.
   */
  private double myRectangleMouseOverHeightRatio;

  /**
   * The gap value as a percentage {0...1} of the height given to each data series.
   */
  private float myHeightGap;

  /**
   * @param reducer class that helps reduce the number of unique points drawn by the state chart.
   */
  public StateChartConfig(@NotNull StateChartReducer<E> reducer) {
    this(reducer, 1, 1, 0.5f);
  }

  /**
   * @param reducer                  class that helps reduce the number of unique points drawn by the state chart.
   * @param rectHeightRatio          height value as a percentage {0...1} that represents the height of rectangles created by the state chart.
   * @param rectMouseOverHeightRatio height value as a percentage {0...1} that represents the height of rectangles while the mouse is hovering over them.
   * @param heightGap                The gap value as a percentage {0...1} of the height given to each data series
   */
  public StateChartConfig(@NotNull StateChartReducer<E> reducer,
                          double rectHeightRatio,
                          double rectMouseOverHeightRatio,
                          float heightGap) {
    myReducer = reducer;
    myRectangleHeightRatio = rectHeightRatio;
    myRectangleMouseOverHeightRatio = rectMouseOverHeightRatio;
    myHeightGap = heightGap;
  }

  @NotNull
  public StateChartReducer<E> getReducer() {
    return myReducer;
  }

  public double getRectangleHeightRatio() {
    return myRectangleHeightRatio;
  }

  public double getRectangleMouseOverHeightRatio() {
    return myRectangleMouseOverHeightRatio;
  }

  public float getHeightGap() {
    return myHeightGap;
  }
}