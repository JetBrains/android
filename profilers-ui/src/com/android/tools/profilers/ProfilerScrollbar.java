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
package com.android.tools.profilers;

import com.android.tools.adtui.RangeScrollBarUI;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBScrollBar;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

/**
 * A custom toolbar that synchronizes with the data+view ranges from the {@link ProfilerTimeline}.
 * This control sets the timeline into streaming mode if users drags the thumb all the way to the right.
 */
public final class ProfilerScrollbar extends JBScrollBar {
  /**
   * The percentage of the current view range's length to zoom/pan per mouse wheel click.
   */
  private static final float VIEW_PERCENTAGE_PER_MOUSEHWEEL_FACTOR = 0.005f;

  /**
   * Work in ms to keep things compatible with scrollbar's integer api.
   * This should cover a long enough time period for us in terms of profiling.
   */
  private static final long MS_TO_US = TimeUnit.MILLISECONDS.toMicros(1);

  /**
   * Pixel threshold to switch {@link #myTimeline} to streaming mode.
   */
  private static final float STREAMING_POSITION_THRESHOLD_PX = 10;

  @NotNull private final ProfilerTimeline myTimeline;
  private final AspectObserver myAspectObserver;

  private boolean myUpdating;
  private boolean myCheckStream;

  public ProfilerScrollbar(@NotNull ProfilerTimeline timeline,
                           @NotNull JComponent zoomPanComponent) {
    super(HORIZONTAL);

    myAspectObserver = new AspectObserver();
    myTimeline = timeline;
    myTimeline.getViewRange().addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::modelChanged);
    myTimeline.getDataRange().addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::modelChanged);

    ProfilerScrollbarUi scrollbarUi = new ProfilerScrollbarUi();
    setUI(scrollbarUi);
    addPropertyChangeListener(evt -> {
      // preserve RangeScrollbarUI always, otherwise it reverts back to the default UI when switching themes.
      if (evt.getPropertyName().equals("UI") && !(evt.getNewValue() instanceof RangeScrollBarUI)) {
        setUI(scrollbarUi);
      }
    });

    addAdjustmentListener(e -> {
      if (!myUpdating) {
        updateModel();
        if (!e.getValueIsAdjusting()) {
          myCheckStream = true;
        }
      }
    });
    zoomPanComponent.addMouseWheelListener(e -> {
      if (isScrollable()) {
        int count = e.getWheelRotation();
        double deltaUs = getWheelDelta() * count;
        boolean isMenuKeyDown = SystemInfo.isMac ? e.isMetaDown() : e.isControlDown();
        if (isMenuKeyDown) {
          double anchor = ((float)e.getX() / e.getComponent().getWidth());
          myTimeline.zoom(deltaUs, anchor);
          myCheckStream = deltaUs > 0;
        }
        else {
          myTimeline.pan(deltaUs);
        }
        myCheckStream = deltaUs > 0;
      }
    });
  }

  @VisibleForTesting
  public double getWheelDelta() {
    return myTimeline.getViewRange().getLength() * VIEW_PERCENTAGE_PER_MOUSEHWEEL_FACTOR;
  }

  private void modelChanged() {
    myUpdating = true;
    Range dataRangeUs = myTimeline.getDataRange();
    Range viewRangeUs = myTimeline.getViewRange();
    int dataExtentMs = (int)((dataRangeUs.getLength()) / MS_TO_US);
    int viewExtentMs = Math.min(dataExtentMs, (int)(viewRangeUs.getLength() / MS_TO_US));
    int viewRelativeMinMs = Math.max(0, (int)((viewRangeUs.getMin() - dataRangeUs.getMin()) / MS_TO_US));

    setValues(viewRelativeMinMs, viewExtentMs, 0, dataExtentMs);
    setBlockIncrement(viewExtentMs);
    myUpdating = false;
  }

  private void updateModel() {
    myTimeline.setStreaming(false);
    Range dataRangeUs = myTimeline.getDataRange();
    Range viewRangeUs = myTimeline.getViewRange();
    int valueMs = getValue();
    int viewRelativeMinMs = Math.max(0, (int)((viewRangeUs.getMin() - dataRangeUs.getMin()) / MS_TO_US));
    double deltaUs = (valueMs - viewRelativeMinMs) * MS_TO_US;
    viewRangeUs.shift(deltaUs);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    // Change back to streaming mode as needed
    // Note: isCloseToMax() checks for pixel proximity which relies on the scrollbar's dimension, which is why this code snippet
    // is here instead of animate/postAnimate - we wouldn't get the most current size in those places.
    if (myCheckStream) {
      if (!myTimeline.isStreaming() && isCloseToMax() && myTimeline.canStream()) {
        myTimeline.setStreaming(true);
      }
    }
    myCheckStream = false;
  }

  private boolean isScrollable() {
    BoundedRangeModel model = getModel();
    return model.getMaximum() > model.getExtent();
  }

  private boolean isCloseToMax() {
    BoundedRangeModel model = getModel();
    float snapPercentage = 1 - (STREAMING_POSITION_THRESHOLD_PX / (float)getWidth());
    return (model.getValue() + model.getExtent()) / (float)model.getMaximum() >= snapPercentage;
  }

  private class ProfilerScrollbarUi extends RangeScrollBarUI {
    @Override
    protected void doPaintTrack(Graphics g, JComponent c, Rectangle bounds) {
      g.setColor(ProfilerColors.DEFAULT_BACKGROUND);
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
  }
}
