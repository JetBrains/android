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

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.timeline.AnimatedPan;
import com.android.tools.profilers.timeline.AnimatedZoom;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;

/**
 * A custom toolbar that synchronizes with the data+view ranges from the {@link ProfilerTimeline}.
 *
 * This control sets the timeline into streaming mode if users drags the thumb all the way to the right.
 * Also, any updates back to the timeline object are handled inside {@link #animate(float)} so that
 * view range is synchronized with the {@link Choreographer}. This also means that this control should be
 * registered before/after all {@link Animatable} to ensure the view range does not change in the middle of
 * the animation loop.
 */
public final class ProfilerScrollbar extends JBScrollBar implements Animatable {
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

  private boolean myScrolling;

  public ProfilerScrollbar(@NotNull Choreographer choreographer,
                           @NotNull ProfilerTimeline timeline,
                           @NotNull JComponent zoomPanComponent) {
    super(HORIZONTAL);

    myTimeline = timeline;

    setUI(new ButtonlessScrollBarUI() {
      /**
       * The default ButtonlessScrollBarUI contains logic to overlay the scrollbar
       * and fade in/out on top of a JScrollPane. Because we are using the scrollbar
       * without a scroll pane, it can fade in and out unexpectedly. This subclass
       * simply disables the overlay feature.
       */
      @Override
      protected boolean isMacOverlayScrollbar() {
        return false;
      }
    });

    // We cannot simply use an AdjustmentListener as the values can be changed in multiple scenarios.
    // e.g. streaming vs scrolling vs viewing back in time
    // Here we simply keep track of the state and let the animate loop handles everything.
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (!isScrollable()) {
          return;
        }

        myTimeline.setStreaming(false);
        myScrolling = true;
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (!isScrollable()) {
          return;
        }

        myScrolling = false;
      }
    });

    zoomPanComponent.addMouseWheelListener(e -> {
      int count = e.getWheelRotation();
      double deltaUs = myTimeline.getViewRange().getLength() * VIEW_PERCENTAGE_PER_MOUSEHWEEL_FACTOR * count;
      if (e.isAltDown()) {
        double anchor = ((float)e.getX() / e.getComponent().getWidth());
        AnimatedZoom zoom = new AnimatedZoom(choreographer, myTimeline, deltaUs, anchor);
        choreographer.register(zoom);
      }
      else {
        AnimatedPan pan = new AnimatedPan(choreographer, myTimeline, deltaUs);
        choreographer.register(pan);
      }
    });
  }

  @Override
  public void animate(float frameLength) {
    Range dataRangeUs = myTimeline.getDataRange();
    Range viewRangeUs = myTimeline.getViewRange();
    int dataExtentMs = (int)((dataRangeUs.getLength() - myTimeline.getViewBuffer()) / MS_TO_US);
    int viewExtentMs = Math.min(dataExtentMs, (int)(viewRangeUs.getLength() / MS_TO_US));
    int viewRelativeMinMs = Math.max(0, (int)((viewRangeUs.getMin() - dataRangeUs.getMin()) / MS_TO_US));

    if (myScrolling) {
      int valueMs = getValue();
      setValues(valueMs, viewExtentMs, 0, dataExtentMs);
      double deltaUs = (valueMs - viewRelativeMinMs) * MS_TO_US;
      viewRangeUs.shift(deltaUs);
    }
    else {
      setValues(viewRelativeMinMs, viewExtentMs, 0, dataExtentMs);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    // Change back to streaming mode as needed
    // Note: isCloseToMax() checks for pixel proximity which relies on the scrollbar's dimension, which is why this code snippet
    // is here instead of animate/postAnimate - we wouldn't get the most current size in those places.
    if (!myTimeline.getStreaming() && isCloseToMax() && myTimeline.getStreamLockCount() == 0) {
      myTimeline.setStreaming(true);
    }
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
}
