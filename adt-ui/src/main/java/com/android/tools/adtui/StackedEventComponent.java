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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.event.ActivityAction;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.StackedEventType;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.android.tools.adtui.common.AdtUiUtils.getFittedString;

/**
 * A chart component that renders lines with a title that have the ability to stack.
 */
public class StackedEventComponent extends AnimatedComponent {

  private static final Color DISABLED_ACTION = new JBColor(0xDBDFE2, 0X5E5F60);
  private static final Color ENABLED_ACTION = new JBColor(0x64D8B6, 0x12B0A1);
  private static final Color FONT_COLOR = new JBColor(0x545454, 0xCACACA);
  private static final int CHARACTERS_TO_SHRINK_BY = 1;
  private static final int SEGMENT_SPACING = 5;
  private static final float FONT_PADDING = 10;

  @NotNull
  private final EventModel<StackedEventType> myModel;

  private float myLineThickness = 6.0f;

  /**
   * This map is used to pair actions, to their draw location. This is used primarily to store the
   * location where to draw the name of the incoming event.
   */
  private HashMap<EventAction<StackedEventType>, EventRenderData> myActionToDrawLocationMap = new HashMap<>();
  private List<EventRenderData> myActivities = new ArrayList<>();
  private boolean myRender;

  public StackedEventComponent(@NotNull EventModel<StackedEventType> model) {
    myModel = model;
    setFont(AdtUiUtils.FONT_DEFAULT);
    myModel.addDependency(myAspectObserver).onChange(EventModel.Aspect.EVENT, this::modelChanged);
    myRender = true;
  }

  private void modelChanged() {
    myRender = true;
    opaqueRepaint();
  }

  protected void render() {
    double min = myModel.getRangedSeries().getXRange().getMin();
    double max = myModel.getRangedSeries().getXRange().getMax();

    // A map of EventAction started events to their start time, so we can correlate these to
    // EventAction competed events with the EventAction start events. This is done this way as
    // a event started and completed events may come in in any order at any time.

    // A queue of open index values, this allows us to pack our events without leaving gaps.

    myActivities.clear();
    myActionToDrawLocationMap.clear();
    List<SeriesData<EventAction<StackedEventType>>> series = myModel.getRangedSeries().getSeries();
    int size = series.size();

    // Loop through the data series looking at all of the start events, and stop events.
    // For each start event we store off its EventAction until we find an associated stop event.
    // Once we find a stop event we determine the draw order, name, start and stop locations and
    // cache off a path to draw.
    for (int i = 0; i < size; i++) {
      SeriesData<EventAction<StackedEventType>> seriesData = series.get(i);
      EventAction<StackedEventType> data = seriesData.value;
      Path2D.Float path = new Path2D.Float();
      // Here we normalize the position to a value between 0 and 1. This allows us to scale the width of the line based on the
      // width of our chart.
      double endTime = data.getEndUs() == 0 ? max : data.getEndUs();
      double normalizedEndPosition = ((endTime - min) / (max - min));
      double normalizedstartPosition = ((data.getStartUs() - min) / (max - min));
      path.moveTo(normalizedEndPosition, 1);
      path.lineTo(normalizedstartPosition, 1);
      myActivities.add(new EventRenderData(data, path));
    }

    myActivities.sort((erd1, erd2) -> {
      if (erd1.getAction().getEndUs() == 0 && erd2.getAction().getEndUs() != 0) {
        return -1;
      }
      else if (erd1.getAction().getEndUs() != 0 && erd2.getAction().getEndUs() == 0) {
        return 1;
      }
      else if (erd1.getAction().getEndUs() != 0 && erd2.getAction().getEndUs() != 0) {
        return erd1.getAction().getEndUs() - erd2.getAction().getEndUs() >= 0 ? 1 : -1;
      }
      return erd1.getAction().getStartUs() - erd2.getAction().getStartUs() >= 0 ? 1 : -1;
    });
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    // Set Antialiasing, before we draw anything.
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    if (myRender) {
      render();
      myRender = false;
    }

    int scaleFactor = dim.width;
    double min = myModel.getRangedSeries().getXRange().getMin();
    double max = myModel.getRangedSeries().getXRange().getMax();
    FontMetrics metrics = g2d.getFontMetrics();
    Stroke current = g2d.getStroke();
    BasicStroke str = new BasicStroke(myLineThickness);
    AffineTransform scale = AffineTransform.getScaleInstance(scaleFactor, dim.height - SEGMENT_SPACING);
    Iterator<EventRenderData> itor = myActivities.iterator();
    g2d.setFont(getFont());

    while (itor.hasNext()) {
      g2d.setStroke(str);
      EventRenderData renderData = itor.next();
      EventAction<StackedEventType> event = renderData.getAction();
      if (event.getEndUs() != 0) {
        g2d.setColor(DISABLED_ACTION);
      }
      else {
        g2d.setColor(ENABLED_ACTION);
      }
      Shape shape = scale.createTransformedShape(renderData.getPath());
      g2d.draw(shape);

      g2d.setStroke(current);
      String text = "";
      if (event.getType() != StackedEventType.NONE) {
        text = ((ActivityAction)event).getData();
      }
      double normalizedStartPosition = (event.getStartUs() - min) / (max - min);
      double lifetime = event.getEndUs();
      if (event.getEndUs() == 0) {
        lifetime = max;
      }
      double normalizedEndPosition = (lifetime - min)
                                     / (max - min);
      float startPosition = (float)normalizedStartPosition * scaleFactor;
      float endPosition = (float)normalizedEndPosition * scaleFactor;
      boolean ellipsis = true;

      if (startPosition <= FONT_PADDING) {
        startPosition = FONT_PADDING;
      }

      if (ellipsis) {
        text = getFittedString(metrics, text, endPosition - startPosition, CHARACTERS_TO_SHRINK_BY);
        if (text.isEmpty()) {
          continue;
        }
      }

      g2d.setColor(AdtUiUtils.DEFAULT_FONT_COLOR);
      g2d.drawString(text, startPosition, (myLineThickness + SEGMENT_SPACING));
    }
  }

  public void setLineThickness(float lineThickness) {
    myLineThickness = lineThickness;
  }

  private static class EventRenderData {

    private final EventAction<StackedEventType> mAction;
    private final Path2D mPath;

    public EventAction<StackedEventType> getAction() {
      return mAction;
    }

    public Path2D getPath() {
      return mPath;
    }

    public EventRenderData(EventAction<StackedEventType> action, Path2D path) {
      mAction = action;
      mPath = path;
    }
  }
}

