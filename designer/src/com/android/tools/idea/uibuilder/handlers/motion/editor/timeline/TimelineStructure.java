/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.timeline;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * The basic data structure that describes the timeline
 */
public class TimelineStructure {
  public int[] myXTicksPixels = new int[0];
  public int mTimeLineWidth;
  public int mTimeLineInsetLeft;
  public int mTimeLineInsetRight;
  public float mTimeLineMinValue;
  public float mTimeLineMaxValue;

  public float getTimeCursorMs() {
    return 0;
  }

  public int getCursorPosition() {
    return 0;
  }

  public float getFramePosition() {
    return 0;
  }

  public Color getColorForPosition(float framePosition) {
    return Color.PINK;
  }

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

  public void addWidthChangedListener(PropertyChangeListener listener) {
    this.pcs.addPropertyChangeListener(listener);
  }

  public void removeWidthChanged(PropertyChangeListener listener) {
    this.pcs.removePropertyChangeListener(listener);
  }

  void fireWidthChanged(int oldValue, int newValue) {
    pcs.firePropertyChange("width", oldValue, newValue);
  }

  int floatToPosition(float value) {
    int draw_width = mTimeLineWidth - mTimeLineInsetLeft - mTimeLineInsetRight;
    int ix = (int) (
      draw_width * (value + mTimeLineMinValue) / (mTimeLineMaxValue - mTimeLineMinValue)
        + mTimeLineInsetLeft);
    return ix;
  }
}
