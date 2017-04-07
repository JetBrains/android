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
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A label component that updates its value based on the reporting series passed to it.
 */
public class LegendComponent extends AnimatedComponent {

  public enum Orientation {
    HORIZONTAL,
    VERTICAL,
  }

  private static final BasicStroke LINE_STROKE = new BasicStroke(3);
  private static final BasicStroke BORDER_STROKE = new BasicStroke(1);

  /**
   * Side of the (squared) box icon in pixels.
   */
  private static final int ICON_WIDTH_PX = 10;

  /**
   * Distance, in pixels, between icons and their correspondent labels.
   */
  private static final int ICON_MARGIN_PX = 5;

  /**
   * Vertical space, in pixels, between the legend and the border of the parent component
   * or the next/previous vertical legend.
   */
  private static final int LEGEND_VERTICAL_PADDING_PX = 5;

  /**
   * Distance, in pixels, between legends.
   */
  private int LEGEND_MARGIN_PX = 10;

  /**
   * Min width of the label so that the legends don't shuffle around as the magnitude of the data changes.
   */
  private static final int LABEL_MIN_WIDTH_PX = 100;

  private int mFrequencyMillis;

  private List<JLabel> mLabelsToDraw;

  private long mLastUpdate;

  private List<LegendRenderData> mLegendRenderData;

  private Orientation mOrientation;

  /**
   * Legend component that renders a label, and icon for each series in a chart.
   *
   * @param orientation     Determines if we want the labels to be stacked horizontally or vertically
   * @param frequencyMillis How frequently the labels get updated
   */
  public LegendComponent(Orientation orientation, int frequencyMillis) {
    mFrequencyMillis = frequencyMillis;
    mOrientation = orientation;
    mLastUpdate = 0;
  }

  /**
   * Clears existing LegendRenderData and adds new ones.
   */
  public void setLegendData(List<LegendRenderData> data) {
    mLegendRenderData = new ArrayList<>(data);
    mLabelsToDraw = new ArrayList<>(mLegendRenderData.size());
    for (LegendRenderData initialData : mLegendRenderData) {
      JBLabel label = new JBLabel();
      label.setFont(AdtUiUtils.DEFAULT_FONT);
      mLabelsToDraw.add(label);
    }
  }

  @Override
  protected void updateData() {
    long now = System.currentTimeMillis();
    if (now - mLastUpdate > mFrequencyMillis) {
      mLastUpdate = now;
      for (int i = 0; i < mLegendRenderData.size(); ++i) {
        LegendRenderData data = mLegendRenderData.get(i);
        JLabel label = mLabelsToDraw.get(i);
        if (data.hasData()) {
          label.setText(String.format("%s: %s", data.getLabel(), data.getFormattedData()));
        }
        else {
          label.setText(data.getLabel());
        }
        Dimension preferredSize = label.getPreferredSize();
        if (preferredSize.getWidth() < LABEL_MIN_WIDTH_PX) {
          preferredSize.width = LABEL_MIN_WIDTH_PX;
          label.setPreferredSize(preferredSize);
        }
        label.setBounds(0, 0, preferredSize.width, preferredSize.height);
      }

      // As we adjust the size of the label we need to adjust our own size
      // to tell our parent to give us enough room to draw.
      Dimension newSize = getLegendPreferredSize();
      if (newSize != getPreferredSize()) {
        setPreferredSize(newSize);
        // Set the minimum height of the component to avoid hiding all the labels
        // in case they are longer than the component's total width
        setMinimumSize(new Dimension(getMinimumSize().width, newSize.height));
        revalidate();
      }
    }
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    // TODO: revisit this method and try to simplify it using JBPanels and a LayoutManager.
    Stroke defaultStroke = g2d.getStroke();

    for (int i = 0; i < mLegendRenderData.size(); ++i) {
      LegendRenderData data = mLegendRenderData.get(i);
      JLabel label = mLabelsToDraw.get(i);
      Dimension labelPreferredSize = label.getPreferredSize();
      int xOffset = 0;

      // Draw the icon, and apply a translation offset for the label to be drawn.
      // TODO: Add config for LegendRenderData.IconType.DOTTED_LINE once we support dashed lines.
      if (data.getIcon() == LegendRenderData.IconType.BOX) {
        // Adjust the box initial Y coordinate to align the box and the label vertically.
        int boxY = LEGEND_VERTICAL_PADDING_PX + (labelPreferredSize.height - ICON_WIDTH_PX) / 2;
        Color fillColor = data.getColor();
        g2d.setColor(fillColor);
        g2d.fillRect(0, boxY, ICON_WIDTH_PX, ICON_WIDTH_PX);

        int r = (int)(fillColor.getRed() * .8f);
        int g = (int)(fillColor.getGreen() * .8f);
        int b = (int)(fillColor.getBlue() * .8f);

        Color borderColor = new Color(r,g,b);
        g2d.setColor(borderColor);
        g2d.setStroke(BORDER_STROKE);
        g2d.drawRect(0, boxY, ICON_WIDTH_PX, ICON_WIDTH_PX);
        g2d.setStroke(defaultStroke);

        xOffset = ICON_WIDTH_PX + ICON_MARGIN_PX;
      }
      else if (data.getIcon() == LegendRenderData.IconType.LINE) {
        g2d.setColor(data.getColor());
        g2d.setStroke(LINE_STROKE);
        int lineY = LEGEND_VERTICAL_PADDING_PX + labelPreferredSize.height / 2;
        g2d.drawLine(xOffset, lineY, ICON_WIDTH_PX, lineY);
        g2d.setStroke(defaultStroke);
        xOffset = ICON_WIDTH_PX + ICON_MARGIN_PX;
      }
      g2d.translate(xOffset, LEGEND_VERTICAL_PADDING_PX);
      label.paint(g2d);

      // Translate the draw position for the next set of labels.
      if (mOrientation == Orientation.HORIZONTAL) {
        g2d.translate(labelPreferredSize.width + LEGEND_MARGIN_PX, -LEGEND_VERTICAL_PADDING_PX);
      }
      else if (mOrientation == Orientation.VERTICAL) {
        g2d.translate(-xOffset, labelPreferredSize.height + LEGEND_VERTICAL_PADDING_PX);
      }
    }
  }

  private Dimension getLegendPreferredSize() {
    int totalWidth = 0;
    int totalHeight = 0;
    // Using line icon (vs box icon) because it's wider. Extra space is better than lack of space.
    int iconPaddedSize = ICON_WIDTH_PX + ICON_MARGIN_PX + LEGEND_MARGIN_PX;
    // Calculate total size of all icons + labels.
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
    int heightPadding = 2 * LEGEND_VERTICAL_PADDING_PX;
    // In the case of vertical legends, we have vertical padding for all the legends
    if (mOrientation == Orientation.VERTICAL) {
      heightPadding *= mLabelsToDraw.size();
    }
    return new Dimension(totalWidth, totalHeight + heightPadding);
  }
}