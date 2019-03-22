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

package com.android.tools.adtui;

import com.android.tools.adtui.eventrenderer.EventRenderer;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.EventModel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A chart component that renders a series of enumerated events as icons.
 * The code that creates this class is also responsible for providing a map
 * of {@link EventRenderer}s for rendering each event enu type.
 */
public class EventComponent<E extends Enum<E>> extends AnimatedComponent {

  @NotNull
  private final EventModel<E> myModel;

  @NotNull
  private final Map<E, EventRenderer<E>> mRenderers;

  @NotNull
  private final ArrayList<EventRenderData<E>> mIconsToDraw;

  private boolean myRender;

  private double myMouseX;

  /**
   * Component that renders EventActions as a series of icons.
   */
  public EventComponent(@NotNull EventModel<E> model, @NotNull Map<E, EventRenderer<E>> renderers) {
    myModel = model;
    mRenderers = renderers;
    mIconsToDraw = new ArrayList<>();
    myRender = true;
    myModel.addDependency(myAspectObserver).onChange(EventModel.Aspect.EVENT, this::modelChanged);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        myMouseX = -1;
      }
    });
    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        myMouseX = e.getX();
      }
    });
  }

  private void modelChanged() {
    myRender = true;
    opaqueRepaint();
  }

  protected void render() {
    double max = myModel.getRangedSeries().getXRange().getMax();
    mIconsToDraw.clear();
    List<SeriesData<EventAction<E>>> series = myModel.getRangedSeries().getSeries();
    int size = series.size();

    for (int i = 0; i < size; i++) {
      SeriesData<EventAction<E>> seriesData = series.get(i);
      EventAction<E> data = seriesData.value;
      long endTimeUs = data.getEndUs() == 0L ? (long)max : data.getEndUs();
      mIconsToDraw.add(new EventRenderData<>(data.getStartUs(), endTimeUs, data));
    }
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    if (myRender) {
      render();
      myRender = false;
    }
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    double viewMin = myModel.getRangedSeries().getXRange().getMin();
    double viewMax = myModel.getRangedSeries().getXRange().getMax();
    double scaleFactor = dim.getWidth();
    int mouseOverIndex = -1;
    for (int i = 0; i < mIconsToDraw.size(); i++) {
      EventRenderData<E> data = mIconsToDraw.get(i);

      // We want to start drawing from the beginning of the event or the beginning of a new data stream
      // TODO(b/122964201) Modify the data provider to provide the correct range instead of clamping
      double dataClampedStartTimestamp = Math.max(data.getStartTimestamp(), myModel.getRangedSeries().getIntersection().getMin());
      double normalizedPositionStart = ((dataClampedStartTimestamp - viewMin) / (viewMax - viewMin));
      double normalizedPositionEnd = ((data.getEndTimestamp() - viewMin) / (viewMax - viewMin));
      double normalizedMouseX = myMouseX / scaleFactor;
      boolean isMouseOverElement = normalizedMouseX > normalizedPositionStart && normalizedMouseX <= normalizedPositionEnd;
      // Find the first element we moused over
      if (isMouseOverElement && mouseOverIndex == -1) {
        // Cache off index to draw last so this element draws on top of all other items.
        mouseOverIndex = i;
      }
      else {
        drawEvent(data, g2d, viewMin, viewMax, scaleFactor, false);
      }
    }
    if (mouseOverIndex >= 0) {
      drawEvent(mIconsToDraw.get(mouseOverIndex), g2d, viewMin, viewMax, scaleFactor, true);
    }
  }

  /**
   * Helper function to call into renderer and draw event elements.
   *
   * @param data               Current {@link EventRenderData} element used to grab the event action, as well as normalized positions.
   * @param g2d                Graphics context to draw to.
   * @param min                View range min value.
   * @param max                View range max value.
   * @param scaleFactor        Amount to scale normalized values up by.
   * @param isMouseOverElement If the mouse is over the supplied {@link EventRenderData}
   */
  private void drawEvent(EventRenderData<E> data, Graphics2D g2d, double min, double max, double scaleFactor, boolean isMouseOverElement) {

    // We want to start drawing from the beginning of the event or the beginning of a new data stream
    // TODO(b/122964201) Modify the data provider to provide the correct range instead of clamping
    double dataClampedStartTimestamp = Math.max(data.getStartTimestamp(), myModel.getRangedSeries().getIntersection().getMin());
    double normalizedPositionStart = ((dataClampedStartTimestamp - min) / (max - min));
    double normalizedPositionEnd = ((data.getEndTimestamp() - min) / (max - min));

    AffineTransform translate = AffineTransform
      .getTranslateInstance(normalizedPositionStart * scaleFactor, 0);
    EventAction<E> action = data.getAction();
    EventRenderer<E> renderer = mRenderers.get(action.getType());
    renderer.draw(this,
                  g2d,
                  translate,
                  (normalizedPositionEnd - normalizedPositionStart) * scaleFactor,
                  isMouseOverElement,
                  action);
  }

  private static class EventRenderData<E> {
    private final long mStartTimestamp;
    private final long mEndTimestamp;
    private final EventAction<E> mAction;

    public EventAction<E> getAction() {
      return mAction;
    }

    public long getStartTimestamp() {
      return mStartTimestamp;
    }

    public long getEndTimestamp() {
      return mEndTimestamp;
    }

    public EventRenderData(long startTimestamp, long endTimestamp, EventAction<E> action) {
      mStartTimestamp = startTimestamp;
      mEndTimestamp = endTimestamp;
      mAction = action;
    }
  }
}

