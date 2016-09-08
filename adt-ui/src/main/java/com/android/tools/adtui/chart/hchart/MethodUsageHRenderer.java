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
package com.android.tools.adtui.chart.hchart;

import java.awt.*;

public abstract class MethodUsageHRenderer extends HRenderer<MethodUsage> {

  private static final Color END_COLOR = new Color(0xFF6F00);
  private static final Color START_COLOR = new Color(0xF0CB35);
  private final int mRedDelta;
  private final int mGreenDelta;
  private final int mBlueDelta;

  public MethodUsageHRenderer() {
    super();
    mRedDelta = END_COLOR.getRed() - START_COLOR.getRed();
    mGreenDelta = END_COLOR.getGreen() - START_COLOR.getGreen();
    mBlueDelta = END_COLOR.getBlue() - START_COLOR.getBlue();
  }

  @Override
  protected Color getBordColor(MethodUsage method) {
    return Color.GRAY;
  }

  @Override
  protected Color getFillColor(MethodUsage method) {
    return new Color(
      (int)(START_COLOR.getRed() + method.getInclusivePercentage() * mRedDelta),
      (int)(START_COLOR.getGreen() + method.getInclusivePercentage() * mGreenDelta),
      (int)(START_COLOR.getBlue() + method.getInclusivePercentage() * mBlueDelta));
  }
}