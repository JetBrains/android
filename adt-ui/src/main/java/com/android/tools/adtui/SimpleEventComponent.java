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

import com.android.annotations.NonNull;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.EventRenderData;
import com.android.tools.adtui.model.RangedSimpleSeries;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * A chart component that renders a series of events as icons.
 */
public class SimpleEventComponent<E extends Enum<E>> extends AnimatedComponent {

  public static final Color LINE_COLOR = new Color(166, 129, 184);
  private static final int LINE_OFFSET = 3;
  private static final int NORMALIZED_END = 1;

  /**
   * Enum that defines the action of an event, used when the event has a Down, and Up action such
   * as MouseClick
   */
  public enum Action {
    DOWN,
    UP
  }

  private static final int HOLD_DELAY_MS = 150;

  @NonNull
  private final RangedSimpleSeries<EventAction<Action, E>> mData;

  @NonNull
  private final BufferedImage[] mIcons;

  @NonNull
  private final ArrayList<Shape> mPaths;

  @NonNull
  private final ArrayList<EventRenderData> mIconsToDraw;

  @NonNull
  private final int mImageWidth;

  @NonNull
  private final int mImageHeight;

  /**
   * Component that renders EventActions as a series of icons.
   */
  public SimpleEventComponent(
    @NonNull RangedSimpleSeries<EventAction<Action, E>> data,
    @NonNull BufferedImage[] icons) {
    mData = data;
    mIcons = icons;
    mPaths = new ArrayList<Shape>();
    mIconsToDraw = new ArrayList<EventRenderData>();
    mImageWidth = mIcons[0].getWidth();
    mImageHeight = mIcons[0].getHeight();
  }

  @Override
  protected void updateData() {
    //TODO Pull logic of combining events out of component and into EventHandler
    double min = mData.getRange().getMin();
    double max = mData.getRange().getMax();
    EventAction<Action, ? extends Enum> downEvent = null;
    mIconsToDraw.clear();
    mPaths.clear();
    long now = System.currentTimeMillis();
    for (int i = 0; i < mData.getSeries().size(); i++) {
      EventAction<Action, ? extends Enum> data = mData.getSeries().get(i);
      if (data.getValue() == Action.DOWN) {
        downEvent = data;
      }
      else if (data.getValue() == Action.UP) {
        assert downEvent != null;
        Integer toDraw = data.getValueData().ordinal();
        if (data.getEnd() - downEvent.getStart() >= HOLD_DELAY_MS) {
          toDraw = downEvent.getValueData().ordinal();
          Path2D.Float path = new Path2D.Float();
          double start = (downEvent.getStart() - min) / (max - min);
          double end = (data.getEnd() - min) / (max - min);
          path.moveTo(start, LINE_OFFSET);
          path.lineTo(end, LINE_OFFSET);
          mPaths.add(path);
        }
        mIconsToDraw.add(new EventRenderData(toDraw, data.getEnd()));
        downEvent = null;
      }
    }
    if (downEvent != null) {
      double start = (downEvent.getStart() - min) / (max - min);
      Integer toDraw = downEvent.getValueData().ordinal();
      if (now - downEvent.getStart() >= HOLD_DELAY_MS) {
        Path2D.Float path = new Path2D.Float();
        double end = 1;
        path.moveTo(start, LINE_OFFSET);
        path.lineTo(end, LINE_OFFSET);
        mPaths.add(path);
        mIconsToDraw.add(new EventRenderData(toDraw, downEvent.getEnd()));
      }
    }
  }

  @Override
  protected void draw(Graphics2D g2d) {
    Dimension dim = getSize();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    double min = mData.getRange().getMin();
    double max = mData.getRange().getMax();
    double scaleFactor = dim.getWidth();
    AffineTransform scale = AffineTransform.getScaleInstance(scaleFactor, 1);
    for (int i = 0; i < mIconsToDraw.size(); i++) {
      EventRenderData data = mIconsToDraw.get(i);
      double normalizedPosition = ((data.getTimestamp() - min) / (max - min));
      if (data.getTimestamp() == 0) {
        normalizedPosition = NORMALIZED_END;
      }
      //TODO account for image width in positioning.
      AffineTransform translate = AffineTransform
        .getTranslateInstance(normalizedPosition * scaleFactor - mImageWidth / 2.0, 0);
      g2d.drawImage(mIcons[data.getIndex() % mIcons.length], translate, null);
    }
    Stroke current = g2d.getStroke();
    BasicStroke str = new BasicStroke(2.0f);
    g2d.setStroke(str);
    g2d.setColor(LINE_COLOR);
    for (int i = 0; i < mPaths.size(); i++) {
      Shape shape = scale.createTransformedShape(mPaths.get(i));
      g2d.draw(shape);
    }
    g2d.setStroke(current);
  }

  @Override
  protected void debugDraw(Graphics2D g) {
    super.debugDraw(g);
  }
}

