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
import gnu.trove.TFloatArrayList;

import java.awt.*;
import java.awt.geom.Line2D;

import static com.android.tools.adtui.AxisComponent.AxisOrientation.LEFT;
import static com.android.tools.adtui.AxisComponent.AxisOrientation.RIGHT;

/**
 * A component that draw an axis based on data from a {@link Range} object.
 */
public final class AxisComponent extends AnimatedComponent {

  public enum AxisOrientation {
    LEFT,
    BOTTOM,
    RIGHT,
    TOP
  }

  private static final Font DEFAULT_FONT = new Font("Sans", Font.PLAIN, 10);
  private static final Color TEXT_COLOR = new Color(128, 128, 128);
  private static final int MAJOR_MARKER_LENGTH = 12;
  private static final int MINOR_MARKER_LENGTH = 3;
  private static final int MARKER_LABEL_MARGIN = 2;
  private static final int LABEL_BOUNDS_OFFSET = 5;
  private static final int MAXIMUM_LABEL_WIDTH = 50;

  /**
   * The axis to which this axis should sync its intervals to.
   */
  private AxisComponent mParentAxis;

  /**
   * The Range object that drives this axis.
   */
  @NonNull
  private final Range mRange;

  /**
   * The Global range object.
   */
  @NonNull
  private final Range mGlobalRange;

  /**
   * Name of the axis.
   */
  @NonNull
  private final String mLabel;

  /**
   * The font metrics of the tick labels.
   */
  @NonNull
  private final FontMetrics mMetrics;

  /**
   * Orientation of the axis.
   */
  @NonNull
  private final AxisOrientation mOrientation;

  /**
   * Margin before the start of the axis.
   */
  private final int mStartMargin;

  /**
   * Margin after the end of the axis.
   */
  private final int mEndMargin;

  /**
   * Display min/max values on the axis.
   */
  private boolean mShowMinMax;

  /**
   * Domain object for the axis.
   */
  @NonNull
  private final BaseAxisDomain mDomain;

  /**
   * Interpolated/Animated max value.
   */
  private double mCurrentMaxValue;

  /**
   * Interpolated/Animated min value.
   */
  private double mCurrentMinValue;

  /**
   * Length of the axis in pixels - used for internal calculation.
   */
  private int mAxisLength;

  /**
   * Calculated - Interval value per major marker.
   */
  private float mMajorInterval;

  /**
   * Calculated - Interval value per minor marker.
   */
  private float mMinorInterval;

  /**
   * Calculated - Number of pixels per major interval.
   */
  private float mMajorScale;

  /**
   * Calculated - Number of pixels per minor interval.
   */
  private float mMinorScale;

  /**
   * Calculated - Value of first major marker.
   */
  private double mFirstMarkerValue;

  /**
   * Cached major marker positions.
   */
  private final TFloatArrayList mMajorMarkerPositions;

  /**
   * Cached minor marker positions.
   */
  private final TFloatArrayList mMinorMarkerPositions;

  /**
   * @param range       A Range object this AxisComponent listens to for the min/max values.
   * @param globalRange The global min/max range.
   * @param label       The label/name of the axis.
   * @param orientation The orientation of the axis.
   * @param startMargin Space (in pixels) before the start of the axis.
   * @param endMargin   Space (in pixels) after the end of the axis.
   * @param showMinMax  If true, min/max values are shown on the axis.
   * @param domain      Domain used for formatting the tick markers.
   */
  public AxisComponent(@NonNull Range range, @NonNull Range globalRange,
                       @NonNull String label, @NonNull AxisOrientation orientation,
                       int startMargin, int endMargin, boolean showMinMax, @NonNull BaseAxisDomain domain) {
    mRange = range;
    mGlobalRange = globalRange;
    mLabel = label;
    mOrientation = orientation;
    mShowMinMax = showMinMax;
    mDomain = domain;
    mMajorMarkerPositions = new TFloatArrayList();
    mMinorMarkerPositions = new TFloatArrayList();

    // Leaves space before and after the axis, this helps to prevent the start/end labels from being clipped.
    // TODO these margins complicate the draw code, an alternative is to implement the labels as a different Component,
    // so its draw region is not clipped by the length of the axis.
    mStartMargin = startMargin;
    mEndMargin = endMargin;

    mMetrics = getFontMetrics(DEFAULT_FONT);
  }

  /**
   * When assigned a parent, the tick interval calculations are
   * sync'd to the parent so that their major intervals would have the same scale.
   */
  public void setParentAxis(AxisComponent parent) {
    mParentAxis = parent;
  }

  @NonNull
  public AxisOrientation getOrientation() {
    return mOrientation;
  }

  @NonNull
  public TFloatArrayList getMajorMarkerPositions() {
    return mMajorMarkerPositions;
  }

  /**
   * Returns the position where a value would appear on this axis.
   */
  public float getPositionAtValue(double value) {
    float offset = (float)(mMinorScale * (value - mCurrentMinValue) / mMinorInterval);
    float ret = 0;
    switch (mOrientation) {
      case LEFT:
      case RIGHT:
        // Vertical axes are drawn from bottom to top so reverse the offset.
        ret = 1 - offset;
        break;
      case TOP:
      case BOTTOM:
        ret = offset;
        break;
    }

    return ret * mAxisLength;
  }

  /**
   * Returns the value corresponding to a pixel position on the axis.
   */
  public double getValueAtPosition(int position) {
    float offset = 0;
    switch (mOrientation) {
      case LEFT:
      case RIGHT:
        // Vertical axes are drawn from bottom to top so reverse the position.
        offset = mAxisLength - position;
        break;
      case TOP:
      case BOTTOM:
        offset = position;
        break;
    }

    float normalizedOffset = offset / mAxisLength;
    return mCurrentMinValue + mMinorInterval * normalizedOffset / mMinorScale;
  }

  /**
   * Returns the formatted value corresponding to a pixel position on the axis.
   * The formatting depends on the {@link BaseAxisDomain} object associated
   * with this axis.
   *
   * e.g. For a value of 1500 in milliseconds, this will return "1.5s".
   */
  @NonNull
  public String getFormattedValueAtPosition(int position) {
    return mDomain.getFormattedString(mGlobalRange.getLength(), getValueAtPosition(position));
  }

  @Override
  protected void updateData() {
    mMajorMarkerPositions.reset();
    mMinorMarkerPositions.reset();
    mCurrentMinValue = mRange.getMin();
    mCurrentMaxValue = mRange.getMax();

    double range = mRange.getLength();
    if (mParentAxis != null) {
      mMajorScale = mMinorScale = mParentAxis.mMajorScale;
      mMajorInterval = mMinorInterval = (float)range * mMajorScale;
    }
    else {
      mMajorInterval = mDomain.getMajorInterval(range);
      mMajorScale = (float)(mMajorInterval / range);
      mMinorInterval = mDomain.getMinorInterval(mMajorInterval);
      mMinorScale = (float)(mMinorInterval / range);
    }

    // Calculate the value and offset of the first major marker
    mFirstMarkerValue = Math.floor(mCurrentMinValue / mMajorInterval) * mMajorInterval;
    // Percentage offset of first major marker.
    float firstMarkerOffset = (float)(mMinorScale * (mFirstMarkerValue - mCurrentMinValue) / mMinorInterval);

    // Calculate marker positions
    int numMarkers = (int)Math.floor((mCurrentMaxValue - mFirstMarkerValue) / mMinorInterval) + 1;
    int numMinorPerMajor = (int)(mMajorInterval / mMinorInterval);
    for (int i = 0; i < numMarkers; i++) {
      float markerOffset = firstMarkerOffset + i * mMinorScale;
      if (i % numMinorPerMajor == 0) {    // Major Tick.
        mMajorMarkerPositions.add(markerOffset);
      }
      else {
        mMinorMarkerPositions.add(markerOffset);
      }
    }
  }

  @Override
  protected void draw(Graphics2D g) {
    // Calculate drawing parameters.
    Point startPoint = new Point();
    Point endPoint = new Point();
    Dimension dimension = getSize();
    switch (mOrientation) {
      case LEFT:
        startPoint.x = endPoint.x = dimension.width - 1;
        startPoint.y = dimension.height - mStartMargin - 1;
        endPoint.y = mEndMargin;
        mAxisLength = startPoint.y - endPoint.y;
        break;
      case BOTTOM:
        startPoint.x = mStartMargin;
        endPoint.x = dimension.width - mEndMargin - 1;
        startPoint.y = endPoint.y = 0;
        mAxisLength = endPoint.x - startPoint.x;
        break;
      case RIGHT:
        startPoint.x = endPoint.x = 0;
        startPoint.y = dimension.height - mStartMargin - 1;
        endPoint.y = mEndMargin;
        mAxisLength = startPoint.y - endPoint.y;
        break;
      case TOP:
        startPoint.x = mStartMargin;
        endPoint.x = dimension.width - mEndMargin - 1;
        startPoint.y = endPoint.y = dimension.height - 1;
        mAxisLength = endPoint.x - startPoint.x;
        break;
    }

    if (mAxisLength > 0) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Draw axis.
      g.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);

      // TODO account for pixel spacing so we can skip ticks if the length is too narrow.
      drawMarkers(g, startPoint);

      // TODO draw axis label.
    }
  }

  private void drawMarkers(Graphics2D g2d, Point origin) {
    g2d.setFont(DEFAULT_FONT);
    g2d.setColor(TEXT_COLOR);

    if (mShowMinMax) {
      drawMarkerLabel(g2d, LABEL_BOUNDS_OFFSET, origin, mCurrentMinValue, true);
      drawMarkerLabel(g2d, mAxisLength - LABEL_BOUNDS_OFFSET, origin, mCurrentMaxValue, true);
    }

    // TODO fade in/out markers.
    Line2D.Float line = new Line2D.Float();

    // Draw minor ticks.
    for (int i = 0; i < mMinorMarkerPositions.size(); i++) {
      if (mMinorMarkerPositions.get(i) >= 0) {
        float scaledPosition = mMinorMarkerPositions.get(i) * mAxisLength;
        drawMarkerLine(g2d, line, scaledPosition, origin, false);
      }
    }

    // Draw major ticks.
    for (int i = 0; i < mMajorMarkerPositions.size(); i++) {
      if (mMajorMarkerPositions.get(i) >= 0) {
        float scaledPosition = mMajorMarkerPositions.get(i) * mAxisLength;
        drawMarkerLine(g2d, line, scaledPosition, origin, true);

        double markerValue = mFirstMarkerValue + i * mMajorInterval;
        drawMarkerLabel(g2d, scaledPosition, origin, markerValue, !mShowMinMax);
      }
    }
  }

  private void drawMarkerLine(Graphics2D g2d, Line2D.Float line, float markerOffset,
                              Point origin, boolean isMajor) {
    float markerStartX = 0, markerStartY = 0, markerEndX = 0, markerEndY = 0;
    int markerLength = isMajor ? MAJOR_MARKER_LENGTH : MINOR_MARKER_LENGTH;
    switch (mOrientation) {
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
    g2d.draw(line);
  }

  private void drawMarkerLabel(Graphics2D g2d, float markerOffset, Point origin,
                               double markerValue, boolean isMinMax) {
    String formattedValue = mDomain.getFormattedString(mGlobalRange.getLength(), markerValue);
    int stringAscent = mMetrics.getAscent();
    int stringLength = mMetrics.stringWidth(formattedValue);

    float labelX, labelY;
    float reserved; // reserved space for min/max labels.
    switch (mOrientation) {
      case LEFT:
        labelX = origin.x - MAJOR_MARKER_LENGTH - MARKER_LABEL_MARGIN - stringLength;
        labelY = origin.y - markerOffset + stringAscent * 0.5f;
        reserved = stringAscent;
        break;
      case RIGHT:
        labelX = MAJOR_MARKER_LENGTH + MARKER_LABEL_MARGIN;
        labelY = origin.y - markerOffset + stringAscent * 0.5f;
        reserved = stringAscent;
        break;
      case TOP:
        labelX = origin.x + markerOffset + MARKER_LABEL_MARGIN;
        labelY = origin.y - MINOR_MARKER_LENGTH;
        reserved = stringLength;
        break;
      case BOTTOM:
        labelX = origin.x + markerOffset + MARKER_LABEL_MARGIN;
        labelY = MINOR_MARKER_LENGTH + stringAscent;
        reserved = stringLength;
        break;
      default:
        throw new AssertionError("Unexpected orientation: " + mOrientation);
    }

    if (isMinMax || (markerOffset - reserved > 0 && markerOffset + reserved < mAxisLength)) {
      g2d.drawString(formattedValue, labelX, labelY);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    int width = MAJOR_MARKER_LENGTH + MARKER_LABEL_MARGIN + MAXIMUM_LABEL_WIDTH;
    int height = mStartMargin + mEndMargin;
    return (mOrientation == LEFT || mOrientation == RIGHT) ? new Dimension(width, height) : new Dimension(height, width);
  }
}
