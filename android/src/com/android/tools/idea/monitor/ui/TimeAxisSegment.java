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
package com.android.tools.idea.monitor.ui;

import com.android.annotations.NonNull;
import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A simple Segment that holds the horizontal time axis, taking advantage of the default BaseSegment
 * implementation with proper column spacing and selection support.
 */
public final class TimeAxisSegment extends BaseSegment {

  @NonNull
  private final AxisComponent mTimeAxis;

  public TimeAxisSegment(@NonNull Range scopedRange, @NonNull AxisComponent timeAxis,
                         @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super("", scopedRange, dispatcher); // Empty label.
    mTimeAxis = timeAxis;
  }

  @Override
  protected void setCenterContent(@NonNull JPanel panel) {
    panel.add(mTimeAxis);
  }

  @Override
  protected void setLeftContent(@NonNull JPanel panel) {
    panel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, AdtUiUtils.DEFAULT_BORDER_COLOR));
  }

  @Override
  protected void setRightContent(@NonNull JPanel panel) {
    panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, AdtUiUtils.DEFAULT_BORDER_COLOR));
  }
}
