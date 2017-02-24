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
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A label component that updates its value based on the reporting series passed to it.
 */
public class LegendComponent extends AnimatedComponent {

  public enum Orientation {
    HORIZONTAL,
    VERTICAL,
  }

  private static final BasicStroke LINE_STROKE = new BasicStroke(3);
  private static final BasicStroke DASH_STROKE = new BasicStroke(2.0f,
                                                                 BasicStroke.CAP_BUTT,
                                                                 BasicStroke.JOIN_BEVEL,
                                                                 10.0f,  // Miter limit, Swing's default
                                                                 new float[]{4.0f, 2.0f},  // Dash pattern in pixel
                                                                 0.0f);  // Dash phase - just starts at zero.)
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
   * Vertical space, in pixels, between the legend and the border.
   */
  private static final int DEFAULT_VERTICAL_PADDING_PX = 5;

  /**
   * Vertical space, in pixels, between legends.
   */
  private static final int LEGEND_VERTICAL_GAP = 10;

  /**
   * Distance, in pixels, between legends.
   */
  private static int LEGEND_MARGIN_PX = 10;

  private final int myVerticalPadding;

  private final LegendComponentModel myModel;

  /**
   * The visual configuration of the legends
   */
  private final Map<Legend, LegendConfig> myConfigs;

  @NotNull
  private List<JLabel> myLabelsToDraw = new ArrayList<>();

  @NotNull
  private Orientation myOrientation;

  /**
   * Legend component that renders a label, and icon for each series in a chart.
   *
   * @param orientation     Determines if we want the labels to be stacked horizontally or vertically
   * @param frequencyMillis How frequently the labels get updated
   */
  public LegendComponent(@NotNull LegendComponentModel model, int verticalPadding) {
    myModel = model;
    myConfigs = new HashMap<>();
    myOrientation = Orientation.HORIZONTAL;
    myVerticalPadding = verticalPadding;
    myModel.addDependency(myAspectObserver)
      .onChange(LegendComponentModel.Aspect.LEGEND, this::modelChanged);
    setFont(AdtUiUtils.DEFAULT_FONT);
    modelChanged();
  }

  public LegendComponent(@NotNull LegendComponentModel model) {
    this(model, DEFAULT_VERTICAL_PADDING_PX);
  }

  public void configure(Legend legend, LegendConfig config) {
    myConfigs.put(legend, config);
  }

  private void modelChanged() {
    int labels = myModel.getLegends().size();
    for (int i = myLabelsToDraw.size(); i < labels; i++) {
      JBLabel label = new JBLabel();
      label.setFont(getFont());
      myLabelsToDraw.add(label);
    }
    if (myLabelsToDraw.size() > labels) {
      myLabelsToDraw.subList(labels, myLabelsToDraw.size()).clear();
    }

    Dimension oldSize = getPreferredSize();
    for (int i = 0; i < myModel.getLegends().size(); i++) {
      JLabel label = myLabelsToDraw.get(i);
      Legend legend = myModel.getLegends().get(i);
      String text = legend.getName();
      String value = legend.getValue();
      if (!text.isEmpty() && StringUtil.isNotEmpty(value)) {
        text += ": ";
      }
      if (value != null) {
        text += value;
      }
      label.setText(text);

      Dimension preferredSize = label.getPreferredSize();
      label.setBounds(0, 0, preferredSize.width, preferredSize.height);
    }
    if (oldSize != getPreferredSize()) {
      revalidate();
    }
  }

  public void setOrientation(@NotNull Orientation orientation) {
    myOrientation = orientation;
  }

  @NotNull
  public LegendComponentModel getModel() {
    return myModel;
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    // TODO: revisit this method and try to simplify it using JBPanels and a LayoutManager.
    Stroke defaultStroke = g2d.getStroke();
    for (int i = 0; i < myModel.getLegends().size(); ++i) {
      Legend data = myModel.getLegends().get(i);
      LegendConfig config = getConfig(data);
      JLabel label = myLabelsToDraw.get(i);
      Dimension labelPreferredSize = label.getPreferredSize();
      int xOffset = 0;

      // Draw the icon, and apply a translation offset for the label to be drawn.
      switch (config.getIcon()) {
        case BOX:
          // Adjust the box initial Y coordinate to align the box and the label vertically.
          int boxY = myVerticalPadding + (labelPreferredSize.height - ICON_WIDTH_PX) / 2;
          Color fillColor = config.getColor();
          g2d.setColor(fillColor);
          g2d.fillRect(0, boxY, ICON_WIDTH_PX, ICON_WIDTH_PX);

          int r = (int)(fillColor.getRed() * .8f);
          int g = (int)(fillColor.getGreen() * .8f);
          int b = (int)(fillColor.getBlue() * .8f);

          Color borderColor = new Color(r, g, b);
          g2d.setColor(borderColor);
          g2d.setStroke(BORDER_STROKE);
          g2d.drawRect(0, boxY, ICON_WIDTH_PX, ICON_WIDTH_PX);
          g2d.setStroke(defaultStroke);

          xOffset = ICON_WIDTH_PX + ICON_MARGIN_PX;
          break;
        case LINE:
        case DASHED_LINE:
          g2d.setColor(config.getColor());
          g2d.setStroke(config.getIcon() == LegendConfig.IconType.LINE ? LINE_STROKE : DASH_STROKE);
          int lineY = myVerticalPadding + labelPreferredSize.height / 2;
          g2d.drawLine(xOffset, lineY, ICON_WIDTH_PX, lineY);
          g2d.setStroke(defaultStroke);
          xOffset = ICON_WIDTH_PX + ICON_MARGIN_PX;
          break;
        default:
          break;
      }

      g2d.translate(xOffset, myVerticalPadding);
      label.setSize(labelPreferredSize);
      // TODO: use a string instead of a label and call g2d.drawString instead.
      label.paint(g2d);

      // Translate the draw position for the next set of labels.
      if (myOrientation == Orientation.HORIZONTAL) {
        g2d.translate(labelPreferredSize.width + LEGEND_MARGIN_PX, -myVerticalPadding);
      }
      else if (myOrientation == Orientation.VERTICAL) {
        g2d.translate(-xOffset, -myVerticalPadding + labelPreferredSize.height + LEGEND_VERTICAL_GAP);
      }
    }
  }

  private LegendConfig getConfig(Legend data) {
    LegendConfig config = myConfigs.get(data);
    if (config == null) {
      config = new LegendConfig(LegendConfig.IconType.BOX, Color.RED);
      myConfigs.put(data, config);
    }
    return config;
  }

  @Override
  public Dimension getPreferredSize() {
    int totalWidth = 0;
    int totalHeight = 0;
    // Using line icon (vs box icon) because it's wider. Extra space is better than lack of space.
    int iconPaddedSize = ICON_WIDTH_PX + ICON_MARGIN_PX + LEGEND_MARGIN_PX;
    // Calculate total size of all icons + labels.
    for (JLabel label : myLabelsToDraw) {
      Dimension size = label.getPreferredSize();
      if (myOrientation == Orientation.HORIZONTAL) {
        totalWidth += iconPaddedSize + size.width;
        if (totalHeight < size.height) {
          totalHeight = size.height;
        }
      }
      else if (myOrientation == Orientation.VERTICAL) {
        totalHeight += size.height;
        if (totalWidth < size.width + iconPaddedSize) {
          totalWidth = size.width + iconPaddedSize;
        }
      }
    }
    if (myOrientation == Orientation.VERTICAL) {
      totalHeight += (myLabelsToDraw.size() - 1) * LEGEND_VERTICAL_GAP;
    }
    return new Dimension(totalWidth, totalHeight + 2 * myVerticalPadding);
  }

  @Override
  public Dimension getMinimumSize() {
    return this.getPreferredSize();
  }

  @VisibleForTesting
  ImmutableList<JLabel> getLabelsToDraw() {
    return ImmutableList.copyOf(myLabelsToDraw);
  }
}