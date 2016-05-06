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
import com.android.tools.adtui.model.LegendRenderData;
import com.android.tools.adtui.model.ReportingSeries;

import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A label component that updates its value based on the reporting series passed to it.
 */
public class LegendComponent extends AnimatedComponent {

  public enum Orientation {
    HORIZONTAL,
    VERTICAL,
  }

  private static final int LINE_THICKNESS = 4;
  private static final int ICON_WIDTH = 16;
  private static final int ICON_PADDING = 2;
  private static final int LABEL_PADDING = 8;

  @NonNull
  private List<LegendRenderData> mLabels;

  @NonNull
  private BaseAxisDomain mAxisDomain;

  @NonNull
  private int mFrequencyMillis;

  @NonNull
  private List<JLabel> mLabelsToDraw;

  @NonNull
  private long mLastUpdate;

  @NonNull
  private List<LegendRenderData> mLegendRenderDatas;

  private Orientation mOrientation;

  /**
   * Legend component that renders a label, and icon for each series in a chart.
   *
   * @param legendRenderDatas An list of labels, icons, and colors to be used in the rendering of each label. If the series is null
   *                          only the label will be rendered
   * @param orientation       Determines if we want the labels to be stacked horizontally or vertically
   * @param frequencyMillis   How frequently the labels get updated
   * @param domain            The conversion function to use for the data from the series to the label.
   */
  public LegendComponent(List<LegendRenderData> legendRenderDatas, Orientation orientation, int frequencyMillis, BaseAxisDomain domain) {
    mLegendRenderDatas = legendRenderDatas;
    mAxisDomain = domain;
    mFrequencyMillis = frequencyMillis;
    mOrientation = orientation;
    mLastUpdate = 0;
    initialize();
  }

  private void initialize() {
    mLabelsToDraw = new ArrayList<>(mLegendRenderDatas.size());
    for (LegendRenderData data : mLegendRenderDatas) {
      JLabel label = new JLabel();
      mLabelsToDraw.add(label);
    }
  }

  @Override
  protected void updateData() {
    long now = System.currentTimeMillis();
    if (now - mLastUpdate > mFrequencyMillis) {
      mLastUpdate = now;
      for (int i = 0; i < mLegendRenderDatas.size(); ++i) {
        LegendRenderData data = mLegendRenderDatas.get(i);
        ReportingSeries series = data.getSeries();
        JLabel label = mLabelsToDraw.get(i);
        Dimension preferredSize = label.getPreferredSize();
        label.setBounds(0, 0, preferredSize.width, preferredSize.height);
        if (series != null) {
          Collection<ReportingSeries.ReportingData> latestDataList = series.getFullReportingData((long)series.getLatestValue());
          //TODO change getFullReportingData to return a single instance.
          for (ReportingSeries.ReportingData latestData : latestDataList) {
            label.setText(String.format("%s: %s", latestData.label, latestData.formattedXData));
          }
        }
      }

      //As we adjust the size of the label we need to adjust the size of ourself
      //to tell our parent to give us enough room to draw.
      Dimension dimension = getPreferredSize();
      setSize(dimension.width, dimension.height);
    }
  }

  @Override
  protected void draw(Graphics2D g2d) {
    for (int i = 0; i < mLegendRenderDatas.size(); ++i) {
      LegendRenderData data = mLegendRenderDatas.get(i);
      JLabel label = mLabelsToDraw.get(i);
      Dimension preferredSize = label.getPreferredSize();
      int xOffset = 0;
      //Draw the icon, and apply a translation offset for the label to be drawn.
      if (data.getIcon() == LegendRenderData.IconType.BOX) {
        g2d.setColor(data.getColor());
        g2d.fillRect(xOffset, 0, ICON_WIDTH, ICON_WIDTH);
        xOffset = ICON_WIDTH + ICON_PADDING;
      }
      else if (data.getIcon() == LegendRenderData.IconType.LINE) {
        g2d.setColor(data.getColor());
        Stroke defaultStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(LINE_THICKNESS));
        g2d.drawLine(xOffset, preferredSize.height / 2, xOffset + ICON_WIDTH, preferredSize.height / 2);
        g2d.setStroke(defaultStroke);
        xOffset = ICON_WIDTH + ICON_PADDING;
      }
      g2d.translate(xOffset, 0);
      label.paint(g2d);

      //Translate the draw position for the next set of labels.
      if (mOrientation == Orientation.HORIZONTAL) {
        g2d.translate(preferredSize.width + LABEL_PADDING, 0);
      }
      else if (mOrientation == Orientation.VERTICAL) {
        g2d.translate(-xOffset, 0);
        g2d.translate(0, preferredSize.height + LABEL_PADDING);
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    int totalWidth = 0;
    int totalHeight = 0;
    int iconPaddedSize = ICON_WIDTH + ICON_PADDING + LABEL_PADDING;
    //Calculate total size of all icons + labels.
    for (JLabel label : mLabelsToDraw) {
      Dimension size = label.getPreferredSize();
      if (mOrientation == Orientation.HORIZONTAL) {
        totalWidth += iconPaddedSize + size.width;
        if (totalHeight < size.height) {
          totalHeight = size.height;
        }
      }
      else if (mOrientation == Orientation.VERTICAL) {
        totalHeight += iconPaddedSize;
        if (totalWidth < size.width + iconPaddedSize) {
          totalWidth = size.width + iconPaddedSize;
        }
      }
    }
    return new Dimension(totalWidth, totalHeight);
  }
}