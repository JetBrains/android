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

import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.SimpleEventType;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Map;

/**
 * A chart component that renders a series of events as icons.
 */
public class SimpleEventComponent<E extends Enum<E>> extends AnimatedComponent {

  @NotNull
  private final EventModel<SimpleEventType> myModel;

  @NotNull
  private final Map<E, SimpleEventRenderer> mRenderers;

  @NotNull
  private final ArrayList<EventRenderData> mIconsToDraw;

  private boolean myRender;
  /**
   * Component that renders EventActions as a series of icons.
   */
  public SimpleEventComponent(@NotNull EventModel<SimpleEventType> model, @NotNull Map<E, SimpleEventRenderer> renderers) {
    myModel = model;
    mRenderers = renderers;
    mIconsToDraw = new ArrayList<>();
    myRender = true;
    myModel.addDependency(myAspectObserver).onChange(EventModel.Aspect.EVENT, this::modelChanged);
  }

  private void modelChanged() {
    myRender = true;
    opaqueRepaint();
  }

  protected void render() {
    //TODO Pull logic of combining events out of component and into EventHandler
    double max = myModel.getRangedSeries().getXRange().getMax();
    mIconsToDraw.clear();
    ImmutableList<SeriesData<EventAction<SimpleEventType>>> series = myModel.getRangedSeries().getSeries();
    int size = series.size();

    for (int i = 0; i < size; i++) {
      SeriesData<EventAction<SimpleEventType>> seriesData = series.get(i);
      EventAction data = seriesData.value;
      long endTimeUs = data.getEndUs() == 0L ? (long)max : data.getEndUs();
      mIconsToDraw.add(new EventRenderData(data.getStartUs(), endTimeUs, data));
    }
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    if (myRender) {
      render();
      myRender = false;
    }
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    double min = myModel.getRangedSeries().getXRange().getMin();
    double max = myModel.getRangedSeries().getXRange().getMax();
    double scaleFactor = dim.getWidth();
    for (int i = 0; i < mIconsToDraw.size(); i++) {
      EventRenderData data = mIconsToDraw.get(i);
      double normalizedPositionStart = ((data.getStartTimestamp() - min) / (max - min));
      double normalizedPositionEnd = ((data.getEndTimestamp() - min) / (max - min));
      AffineTransform translate = AffineTransform
        .getTranslateInstance(normalizedPositionStart * scaleFactor, 0);
      EventAction<SimpleEventType> action = data.getAction();
      mRenderers.get(action.getType())
        .draw(this, g2d, translate, (normalizedPositionEnd - normalizedPositionStart) * scaleFactor, action);
    }
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

