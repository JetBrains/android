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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.RotatedLabel;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A component that draws an axis based on data from a {@link Range} object.
 */
public final class AxisComponent extends AnimatedComponent {

  public enum AxisOrientation {
    LEFT,
    BOTTOM,
    RIGHT,
    TOP
  }

  private static final BasicStroke DEFAULT_AXIS_STROKE = new BasicStroke(1);
  private static final int MARKER_LABEL_OFFSET_PX = 5;
  private static final int MAXIMUM_LABEL_WIDTH = 50;
  private static final int DEFAULT_MAJOR_MARKER_LENGTH = 10;
  private static final int DEFAULT_MINOR_MARKER_LENGTH = 4;

  @VisibleForTesting
  static final float REMOVE_MAJOR_TICK_DENSITY = 1.1f; // A value that is close to, but doesn't, overlap.

  private static final JBColor DEFAULT_VERT_AXIS_TICK_COLOR = new JBColor(new Color(0, 0, 0, 64), new Color(255, 255, 255, 102));
  private static final JBColor DEFAULT_HORIZ_AXIS_TICK_COLOR = new JBColor(new Color(0xB9B9B9), new Color(0x656464));

  @Nullable private JLabel myLabel;

  /**
   * Length of the axis in pixels - used for internal calculation.
   */
  private int myAxisLength;

  /**
   * Cached major marker positions.
   */
  private final FloatArrayList myMajorMarkerPositions;

  /**
   * Cached minor marker positions.
   */
  private final FloatArrayList myMinorMarkerPositions;

  /**
   * Cached marker labels
   */
  @NotNull private final List<String> myMarkerLabels;

  @NotNull private Color myMarkerColor;

  private float myMarkerLabelDensity;

  /**
   * Cached max marker labels
   */
  private String myMaxLabel;

  /**
   * Cached min marker labels
   */
  private String myMinLabel;

  private AxisComponentModel myModel;

  @NotNull private final AxisOrientation myOrientation;

  private boolean myCalculateMarkers;

  private int myMajorMarkerLength = DEFAULT_MAJOR_MARKER_LENGTH;
  private int myMinorMarkerLength = DEFAULT_MINOR_MARKER_LENGTH;

  private int myStartMargin;
  private int myEndMargin;
  private boolean myShowMin;
  private boolean myShowMax;

  /**
   * Whether to show the unit only on the max label of the y-axis (if true),
   * or show the unit for all y-axis labels (if false)
   */
  private boolean myOnlyShowUnitAtMax;
  private boolean myShowAxisLine = true;

  /**
   * Whether labels of axis markers are shown, true by default
   */
  private boolean myShowLabels = true;

  private boolean myHideTickAtMin;

  private boolean myHideNegativeValues;

  public AxisComponent(@NotNull AxisComponentModel model, @NotNull AxisOrientation orientation, boolean hideNegativeValues) {
    myModel = model;
    myMajorMarkerPositions = new FloatArrayList();
    myMinorMarkerPositions = new FloatArrayList();
    myMarkerLabels = new ArrayList<>();
    myOrientation = orientation;
    myHideNegativeValues = hideNegativeValues;

    switch (myOrientation) {
      case LEFT:
      case RIGHT:
        myMarkerColor = DEFAULT_VERT_AXIS_TICK_COLOR;
        break;
      default:
        myMarkerColor = DEFAULT_HORIZ_AXIS_TICK_COLOR;
    }

    // Only construct and show the axis label if it is set.
    if (!myModel.getLabel().isEmpty()) {
      switch (myOrientation) {
        case LEFT:
        case RIGHT:
          myLabel = new RotatedLabel(myModel.getLabel());
          myLabel.setSize(mDefaultFontMetrics.getHeight(), mDefaultFontMetrics.stringWidth(myModel.getLabel()));
          break;
        case TOP:
        case BOTTOM:
        default:
          myLabel = new JBLabel(myModel.getLabel());
          myLabel.setSize(mDefaultFontMetrics.stringWidth(myModel.getLabel()), mDefaultFontMetrics.getHeight());
      }
      myLabel.setFont(AdtUiUtils.DEFAULT_FONT);
    }

    setForeground(AdtUiUtils.DEFAULT_FONT_COLOR);
    setFont(AdtUiUtils.DEFAULT_FONT);

    myModel.addDependency(myAspectObserver).onChange(AxisComponentModel.Aspect.AXIS, this::modelChanged);

    // Sets the boolean myCalculateMarkers true for the initial markers.
    myCalculateMarkers = true;
  }

  private void modelChanged() {
    myCalculateMarkers = true;
    opaqueRepaint();
  }

  @Nullable
  public String getLabel() {
    return myLabel == null ? null : myLabel.getText();
  }

  @NotNull
  @VisibleForTesting
  public AxisOrientation getOrientation() {
    return myOrientation;
  }

  @VisibleForTesting
  float getMarkerLabelDensity() {
    return myMarkerLabelDensity;
  }

  void calculateMarkers(@NotNull Dimension dimension) {
    myMarkerLabels.clear();
    myMajorMarkerPositions.clear();
    myMinorMarkerPositions.clear();

    double currentMinValueRelative = myModel.getRange().getMin();
    double currentMaxValueRelative = myModel.getRange().getMax();

    // The models' 'zero' value is the lower bound of the data range.
    // Thus, in the case where the lower bound of the range is negative, because
    // we are subtracting, the calculation will increase the relative min and max
    // values by the absolute value of the negative lower bound. This effectively
    // hides any negative lower bound by turning it into a true zero, while increasing
    // the max value the same amount.
    if (myHideNegativeValues) {
      currentMinValueRelative -= myModel.getZero();
      currentMaxValueRelative -= myModel.getZero();
    }

    double range = myModel.getRange().getLength();
    double labelRange = myModel.getDataRange();

    BaseAxisFormatter formatter = myModel.getFormatter();
    // During the postAnimate phase, use the interpolated min/max/range values to calculate the current major and minor intervals that
    // should be used. Based on the interval values, cache the normalized marker positions which will be used during the draw call.
    float majorInterval = formatter.getMajorInterval(range);
    float minorInterval = formatter.getMinorInterval(majorInterval);
    float minorScale = range == 0.0f ? 1.0f : (float)(minorInterval / range);

    // Calculate the value and offset of the first major marker.
    double firstMarkerValue = Math.floor(currentMinValueRelative / majorInterval) * majorInterval;
    // Percentage offset of first major marker.
    float firstMarkerOffset = (float)(minorScale * (firstMarkerValue - currentMinValueRelative) / minorInterval);

    // Calculate marker positions
    int numMarkers = (int)Math.floor((currentMaxValueRelative - firstMarkerValue) / minorInterval) + 1;
    int numMinorPerMajor = (int)(majorInterval / minorInterval);

    // This is approximate.
    int drawableHeight = dimension.height - myStartMargin - myEndMargin;
    int numMajorMarkers = (int)Math.floor((currentMaxValueRelative - firstMarkerValue) / majorInterval) + 1;
    myMarkerLabelDensity =
      (float)drawableHeight / ((float)numMajorMarkers * (float)(mDefaultFontMetrics.getMaxAscent() + mDefaultFontMetrics.getMaxDescent()));

    // We always start from a major marker.
    for (int i = 0; i < numMarkers; i++) {
      // Discard values that is configured out of the marker range, by default this discards negative values.
      double markerValue = firstMarkerValue + i * minorInterval;
      if (!myModel.getMarkerRange().contains(markerValue)) {
        continue;
      }

      // Discard out of bound values, unless it is the major tick mark at the start.
      float markerOffset = firstMarkerOffset + i * minorScale;
      if ((markerOffset < 0 && i > 0) || markerOffset > 1f) {
        continue;
      }

      if (i % numMinorPerMajor == 0) {    // Major Tick.
        myMajorMarkerPositions.add(markerOffset);
        myMarkerLabels.add(formatter.getFormattedString(labelRange, markerValue, !myOnlyShowUnitAtMax));
      }
      else {
        myMinorMarkerPositions.add(markerOffset);
      }
    }

    if (myShowMin && myModel.getMarkerRange().contains(currentMinValueRelative)) {
      myMinLabel = formatter.getFormattedString(labelRange, currentMinValueRelative, !myOnlyShowUnitAtMax);
    }
    if (myShowMax && myModel.getMarkerRange().contains(currentMaxValueRelative)) {
      myMaxLabel = formatter.getFormattedString(labelRange, currentMaxValueRelative, true);
    }
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (myCalculateMarkers) {
      calculateMarkers(dim);
      myCalculateMarkers = false;
    }
    // Calculate drawing parameters.
    Point startPoint = new Point();
    Point endPoint = new Point();
    Point labelPoint = new Point();
    switch (myOrientation) {
      case LEFT:
        startPoint.x = endPoint.x = dim.width - 1;
        startPoint.y = dim.height - myStartMargin - 1;
        endPoint.y = myEndMargin;
        myAxisLength = startPoint.y - endPoint.y;

        //Affix label to top left.
        labelPoint.x = 0;
        labelPoint.y = endPoint.y;
        break;
      case BOTTOM:
        startPoint.x = myStartMargin;
        endPoint.x = dim.width - myEndMargin - 1;
        startPoint.y = endPoint.y = 0;
        myAxisLength = endPoint.x - startPoint.x;

        //Affix label to bottom left
        labelPoint.x = startPoint.x;
        labelPoint.y = getHeight() - (mDefaultFontMetrics.getMaxAscent() + mDefaultFontMetrics.getMaxDescent());
        break;
      case RIGHT:
        startPoint.x = endPoint.x = 0;
        startPoint.y = dim.height - myStartMargin - 1;
        endPoint.y = myEndMargin;
        myAxisLength = startPoint.y - endPoint.y;

        //Affix label to top right
        labelPoint.x = getWidth() - mDefaultFontMetrics.getMaxAdvance();
        labelPoint.y = endPoint.y;
        break;
      case TOP:
        startPoint.x = myStartMargin;
        endPoint.x = dim.width - myEndMargin - 1;
        startPoint.y = endPoint.y = dim.height - 1;
        myAxisLength = endPoint.x - startPoint.x;

        //Affix label to top left
        labelPoint.x = 0;
        labelPoint.y = 0;
        break;
    }

    if (myAxisLength > 0) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setStroke(DEFAULT_AXIS_STROKE);

      if (myShowAxisLine) {
        g.setColor(myMarkerColor);
        g.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);
      }

      // TODO account for pixel spacing so we can skip ticks if the length is too narrow.
      drawMarkers(g, startPoint);

      if (myLabel != null) {
        AffineTransform initialTransform = g.getTransform();
        g.translate(labelPoint.x, labelPoint.y);
        myLabel.paint(g);
        g.setTransform(initialTransform);
      }
    }
  }

  private void drawMarkers(Graphics2D g2d, Point origin) {
    g2d.setFont(getFont());

    if (myShowLabels) {
      if (myShowMin && myMinLabel != null) {
        drawMarkerLabel(g2d, 0, origin, myMinLabel, true);
      }
      if (myShowMax && myMaxLabel != null && myMajorMarkerPositions.size() > 1) {
        drawMarkerLabel(g2d, myAxisLength, origin, myMaxLabel, true);
      }
    }

    // Determine whether or not to skip rendering of internal tick marks.
    boolean skipRendering = (myOrientation == AxisOrientation.LEFT || myOrientation == AxisOrientation.RIGHT) && myMarkerLabelDensity < REMOVE_MAJOR_TICK_DENSITY;

    Line2D.Float line = new Line2D.Float();

    if (!skipRendering) {
      // Draw minor ticks.
      for (int i = 0; i < myMinorMarkerPositions.size(); i++) {
        float scaledPosition = myMinorMarkerPositions.getFloat(i) * myAxisLength;
        drawMarkerLine(g2d, line, scaledPosition, origin, myMinorMarkerLength);
      }
    }

    // Draw major ticks.
    for (int i = 0; i < myMajorMarkerPositions.size(); i++) {
      if (i > 0 && i < myMajorMarkerPositions.size() - 1 && skipRendering) {
        // Skip rendering for interior ticks.
        continue;
      }

      float scaledPosition = myMajorMarkerPositions.getFloat(i) * myAxisLength;
      drawMarkerLine(g2d, line, scaledPosition, origin, myMajorMarkerLength);
      if (myShowLabels) {
        boolean reserveMinMaxBufferZone = myShowMin || myShowMax || (myHideTickAtMin && scaledPosition == 0);
        drawMarkerLabel(g2d, scaledPosition, origin, myMarkerLabels.get(i), !reserveMinMaxBufferZone);
      }
    }
  }

  private void drawMarkerLine(Graphics2D g2d, Line2D.Float line, float markerOffset, Point origin, int markerLength) {
    if (myHideTickAtMin && markerOffset == 0) {
      return;
    }

    float markerStartX = 0, markerStartY = 0, markerEndX = 0, markerEndY = 0;
    switch (myOrientation) {
      case LEFT:
        markerStartX = origin.x - markerLength;
        markerStartY = markerEndY = origin.y - markerOffset;
        markerEndX = origin.x;
        break;
      case RIGHT:
        markerStartX = 0;
        markerStartY = markerEndY = origin.y - markerOffset;
        markerEndX = markerLength;
        break;
      case TOP:
        markerStartX = markerEndX = origin.x + markerOffset;
        markerStartY = origin.y - markerLength;
        markerEndY = origin.y;
        break;
      case BOTTOM:
        markerStartX = markerEndX = origin.x + markerOffset;
        markerStartY = 0;
        markerEndY = markerLength;
        break;
    }

    line.setLine(markerStartX, markerStartY, markerEndX, markerEndY);
    g2d.setColor(myMarkerColor);
    g2d.draw(line);
  }

  private void drawMarkerLabel(Graphics2D g2d, float markerOffset, Point origin, String value, boolean alwaysRender) {
    int stringAscent = mDefaultFontMetrics.getAscent();
    int stringLength = mDefaultFontMetrics.stringWidth(value);

    // Marker label placement positions are as follows:
    // 1. For horizontal axes, offset to the right relative to the marker position
    // 2. For vertical axes, centered around the marker position
    // The offset amount is specified by MARKER_LABEL_OFFSET_PX in both cases.
    float labelX, labelY;
    float reserved; // reserved space for min/max labels.
    switch (myOrientation) {
      case LEFT:
        labelX = origin.x - (myMajorMarkerLength + stringLength + MARKER_LABEL_OFFSET_PX);
        labelY = origin.y - markerOffset + stringAscent * 0.5f;
        reserved = stringAscent;
        break;
      case RIGHT:
        labelX = myMajorMarkerLength + MARKER_LABEL_OFFSET_PX;
        labelY = origin.y - markerOffset + stringAscent * 0.5f;
        reserved = stringAscent;
        break;
      case TOP:
        labelX = origin.x + markerOffset + MARKER_LABEL_OFFSET_PX;
        labelY = origin.y - myMinorMarkerLength;
        reserved = stringLength;
        break;
      case BOTTOM:
        labelX = origin.x + markerOffset + MARKER_LABEL_OFFSET_PX;
        labelY = myMinorMarkerLength + stringAscent;
        reserved = stringLength;
        break;
      default:
        throw new AssertionError("Unexpected orientation: " + myOrientation);
    }

    if (alwaysRender || (markerOffset - reserved > 0 && markerOffset + reserved < myAxisLength)) {
      g2d.setColor(getForeground());
      g2d.drawString(value, labelX, labelY);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    int width = Math.max(myMajorMarkerLength, myMinorMarkerLength) + MARKER_LABEL_OFFSET_PX + MAXIMUM_LABEL_WIDTH;
    int height = 1;
    return (myOrientation == AxisOrientation.LEFT || myOrientation == AxisOrientation.RIGHT) ?
           new Dimension(width, height) : new Dimension(height, width);
  }

  public AxisComponentModel getModel() {
    return myModel;
  }


  public void setShowAxisLine(boolean showAxisLine) {
    myShowAxisLine = showAxisLine;
  }

  public void setShowMin(boolean showMin) {
    myShowMin = showMin;
  }

  public void setShowMax(boolean showMax) {
    myShowMax = showMax;
  }

  public void setOnlyShowUnitAtMax(boolean onlyShowUnitAtMax) {
    myOnlyShowUnitAtMax = onlyShowUnitAtMax;
  }

  public void setHideTickAtMin(boolean hideTickAtMin) {
    myHideTickAtMin = hideTickAtMin;
  }

  public void setMarkerLengths(int majorMarker, int minorMarker) {
    myMajorMarkerLength = majorMarker;
    myMinorMarkerLength = minorMarker;
  }

  public void setMargins(int startMargin, int endMargin) {
    myStartMargin = startMargin;
    myEndMargin = endMargin;
  }

  public void setMarkerColor(@NotNull Color markerColor) {
    myMarkerColor = markerColor;
  }

  public void setShowLabels(boolean show) {
    myShowLabels = show;
  }

  @VisibleForTesting
  String getMinLabel() {
    return myMinLabel;
  }

  @VisibleForTesting
  String getMaxLabel() {
    return myMaxLabel;
  }
}
