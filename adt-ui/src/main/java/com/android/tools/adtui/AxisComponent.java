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
import com.android.tools.adtui.common.RotatedLabel;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.intellij.ui.components.JBLabel;
import gnu.trove.TFloatArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.util.ArrayList;

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
  private static final int MARKER_LABEL_OFFSET_PX = 3;
  private static final int MAXIMUM_LABEL_WIDTH = 50;

  @NotNull private final Range myRange;
  @Nullable private Range myGlobalRange;
  @NotNull private BaseAxisFormatter myFormatter;
  @NotNull private final AxisOrientation myOrientation;

  @Nullable private JLabel myLabel;
  @NotNull private final FontMetrics myMetrics;

  private final int myStartMargin;
  private final int myEndMargin;
  private final int myMajorMarkerLength;
  private final int myMinorMarkerLength;
  private final boolean myShowMin;
  private final boolean myShowMax;
  private final boolean myShowUnitAtMax;
  private boolean myShowAxisLine;

  private AxisComponent myParentAxis;
  private boolean myClampToMajorTicks;

  /**
   * Interpolated/Animated max value.
   */
  private double myCurrentMaxValueRelative;

  /**
   * Interpolated/Animated min value.
   */
  private double myCurrentMinValueRelative;

  /**
   * Length of the axis in pixels - used for internal calculation.
   */
  private int myAxisLength;

  /**
   * Calculated - Interval value per major marker.
   */
  private float myMajorInterval;

  /**
   * Calculated - Interval value per minor marker.
   */
  private float myMinorInterval;

  /**
   * Calculated - Number of pixels per minor interval.
   */
  private float myMinorScale;

  /**
   * Calculated - Number of major ticks that will be rendered based on the target range.
   * This value is used by a child axis to sync its major tick spacing with its parent.
   */
  private float myMajorNumTicksTarget;

  /**
   * Calculated - Value of first major marker.
   */
  private double myFirstMarkerValue;

  /**
   * Cached major marker positions.
   */
  private final TFloatArrayList myMajorMarkerPositions;

  /**
   * Cached minor marker positions.
   */
  private final TFloatArrayList myMinorMarkerPositions;

  /**
   * Cached marker labels
   */
  @NotNull private final java.util.List<String> myMarkerLabels;

  /**
   * Cached max marker lablels
   */
  private String myMaxLabel;

  /**
   * Cached min marker lablels
   */
  private String myMinLabel;

  /**
   * There are cases when we display axis values relative to the Data.
   * For example, when we use axis to display time information by setting {@code myOffset}
   * it will display the time passed since {@code myOffset} instead of current time.
   */
  private final double myOffset;

  /**
   * During the first update, skip the y range interpolation and snap to the initial max value.
   */
  private boolean myFirstUpdate = true;

  private AxisComponent(@NotNull Builder builder) {
    myRange = builder.myRange;
    myGlobalRange = builder.myGlobalRange;
    myOrientation = builder.myOrientation;
    myShowMin = builder.myShowMin;
    myShowMax = builder.myShowMax;
    myShowUnitAtMax = builder.myShowUnitAtMax;
    myShowAxisLine = builder.myShowAxisLine;
    myFormatter = builder.myFormatter;
    myMajorMarkerPositions = new TFloatArrayList();
    myMinorMarkerPositions = new TFloatArrayList();
    myMarkerLabels = new ArrayList<>();
    myClampToMajorTicks = builder.myClampToMajorTicks;
    myParentAxis = builder.myParentAxis;
    myOffset = builder.myOffset;

    myMajorMarkerLength = builder.myMajorMarkerLength;
    myMinorMarkerLength = builder.myMinorMarkerLength;

    myStartMargin = builder.myStartMargin;
    myEndMargin = builder.myEndMargin;

    myMetrics = getFontMetrics(AdtUiUtils.DEFAULT_FONT);

    // Only construct and show the axis label if it is set.
    if (builder.myLabel.length() > 0) {
      switch (myOrientation) {
        case LEFT:
        case RIGHT:
          myLabel = new RotatedLabel(builder.myLabel);
          myLabel.setSize(myMetrics.getHeight(), myMetrics.stringWidth(builder.myLabel));
          break;
        case TOP:
        case BOTTOM:
        default:
          myLabel = new JBLabel(builder.myLabel);
          myLabel.setSize(myMetrics.stringWidth(builder.myLabel), myMetrics.getHeight());
      }
      myLabel.setFont(AdtUiUtils.DEFAULT_FONT);
    }
  }

  @Nullable
  public String getLabel() {
    return myLabel == null ? null : myLabel.getText();
  }

  @NotNull
  public Range getRange() {
    return myRange;
  }

  @Nullable
  public Range getGlobalRange() {
    return myGlobalRange;
  }

  public boolean getShowMin() {
    return myShowMin;
  }

  public boolean getShowMax() {
    return myShowMax;
  }

  public boolean getShowAxisLine() {
    return myShowAxisLine;
  }

  public void setClampToMajorTicks(boolean clamp) {
    myClampToMajorTicks = clamp;
  }

  public boolean getClampToMajorTicks() {
    return myClampToMajorTicks;
  }

  /**
   * Updates the BaseAxisFormatter for this axis which affects how its ticks/labels are calculated and rendered.
   */
  public void setAxisFormatter(BaseAxisFormatter formatter) {
    myFormatter = formatter;
  }

  @NotNull
  public BaseAxisFormatter getAxisFormatter() {
    return myFormatter;
  }

  /**
   * When assigned a parent, the tick interval calculations are
   * sync'd to the parent so that their major intervals would have the same scale.
   */
  public void setParentAxis(AxisComponent parent) {
    myParentAxis = parent;
  }

  @Nullable
  public AxisComponent getParentAxis() {
    return myParentAxis;
  }

  @NotNull
  public AxisOrientation getOrientation() {
    return myOrientation;
  }

  @NotNull
  public TFloatArrayList getMajorMarkerPositions() {
    return myMajorMarkerPositions;
  }

  /**
   * Returns the position where a value would appear on this axis.
   */
  public float getPositionAtValue(double value) {
    float offset = (float)(myMinorScale * ((value - myOffset) - myCurrentMinValueRelative) / myMinorInterval);
    float ret = 0;
    switch (myOrientation) {
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

    return ret * myAxisLength;
  }

  /**
   * Returns the value corresponding to a pixel position on the axis.
   */
  public double getValueAtPosition(int position) {
    float offset = 0;
    switch (myOrientation) {
      case LEFT:
      case RIGHT:
        // Vertical axes are drawn from bottom to top so reverse the position.
        offset = myAxisLength - position;
        break;
      case TOP:
      case BOTTOM:
        offset = position;
        break;
    }

    float normalizedOffset = offset / myAxisLength;
    return myOffset + myCurrentMinValueRelative + myMinorInterval * normalizedOffset / myMinorScale;
  }

  @Override
  protected void updateData() {
    double maxTarget = myRange.getMax() - myOffset;
    double rangeTarget = myRange.getLength();
    double clampedMaxTarget;

    // During the animate/updateData phase, the axis updates the range's max to a new target based on whether:
    // 1. myClampToMajorTicks is enabled
    //    - This would increase the max to an integral multiplier of the major interval.
    // 2. The axis has a parent axis
    //    - This would use the parent axis's major num ticks to calculate its own major interval that would fit rangeTarget while
    //      matching the tick spacing of the parent axis.
    // TODO Handle non-zero min offsets. Currently these features are used only for y axes and a non-zero use case does not exist yet.
    if (myParentAxis == null) {
      long majorInterval = myFormatter.getMajorInterval(rangeTarget);
      myMajorNumTicksTarget = myClampToMajorTicks ? (float)Math.ceil(maxTarget / majorInterval) : (float)(maxTarget / majorInterval);
      clampedMaxTarget = myMajorNumTicksTarget * majorInterval;
    }
    else {
      // TODO fix this. Currently this continually increases clampedMaxTarget based on its previous value.
      long majorInterval = myFormatter.getInterval(rangeTarget, (int)Math.floor(myParentAxis.myMajorNumTicksTarget));
      clampedMaxTarget = myParentAxis.myMajorNumTicksTarget * majorInterval;
    }

    clampedMaxTarget += myOffset;
    float fraction = myFirstUpdate ? 1f : DEFAULT_LERP_FRACTION;
    myRange.setMax(Choreographer.lerp(myRange.getMax(), clampedMaxTarget, fraction, mFrameLength,
                                      (float)(clampedMaxTarget * DEFAULT_LERP_THRESHOLD_PERCENTAGE)));
    myFirstUpdate = false;
  }

  @Override
  public void postAnimate() {
    myMarkerLabels.clear();
    myMajorMarkerPositions.reset();
    myMinorMarkerPositions.reset();
    myCurrentMinValueRelative = myRange.getMin() - myOffset;
    myCurrentMaxValueRelative = myRange.getMax() - myOffset;
    double range = myRange.getLength();
    double labelRange = myGlobalRange == null ? range : myGlobalRange.getLength();

    // During the postAnimate phase, use the interpolated min/max/range values to calculate the current major and minor intervals that
    // should be used. Based on the interval values, cache the normalized marker positions which will be used during the draw call.
    myMajorInterval = myFormatter.getMajorInterval(range);
    myMinorInterval = myFormatter.getMinorInterval(myMajorInterval);
    myMinorScale = (float)(myMinorInterval / range);

    // Calculate the value and offset of the first major marker
    myFirstMarkerValue = Math.floor(myCurrentMinValueRelative / myMajorInterval) * myMajorInterval;
    // Percentage offset of first major marker.
    float firstMarkerOffset = (float)(myMinorScale * (myFirstMarkerValue - myCurrentMinValueRelative) / myMinorInterval);

    // Calculate marker positions
    int numMarkers = (int)Math.floor((myCurrentMaxValueRelative - myFirstMarkerValue) / myMinorInterval) + 1;
    int numMinorPerMajor = (int)(myMajorInterval / myMinorInterval);
    for (int i = 0; i < numMarkers; i++) {
      // Discard negative values (TODO configurable?)
      double markerValue = myFirstMarkerValue + i * myMinorInterval;
      if (markerValue < 0f) {
        continue;
      }

      // Discard out of bound values.
      float markerOffset = firstMarkerOffset + i * myMinorScale;
      if (markerOffset < 0f || markerOffset > 1f) {
        continue;
      }

      if (i % numMinorPerMajor == 0) {    // Major Tick.
        myMajorMarkerPositions.add(markerOffset);
        myMarkerLabels.add(myFormatter.getFormattedString(labelRange, markerValue, !myShowUnitAtMax));
      }
      else {
        myMinorMarkerPositions.add(markerOffset);
      }
    }

    if (myShowMin) {
      myMinLabel = myFormatter.getFormattedString(labelRange, myCurrentMinValueRelative, !myShowUnitAtMax);
    }
    if (myShowMax) {
      myMaxLabel = myFormatter.getFormattedString(labelRange, myCurrentMaxValueRelative, true);
    }
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
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
        labelPoint.y = getHeight() - (myMetrics.getMaxAscent() + myMetrics.getMaxDescent());
        break;
      case RIGHT:
        startPoint.x = endPoint.x = 0;
        startPoint.y = dim.height - myStartMargin - 1;
        endPoint.y = myEndMargin;
        myAxisLength = startPoint.y - endPoint.y;

        //Affix label to top right
        labelPoint.x = getWidth() - myMetrics.getMaxAdvance();
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
      g.setColor(AdtUiUtils.DEFAULT_BORDER_COLOR);
      g.setStroke(DEFAULT_AXIS_STROKE);

      if (myShowAxisLine) {
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
    g2d.setFont(AdtUiUtils.DEFAULT_FONT);

    if (myShowMin && myMinLabel != null) {
      drawMarkerLabel(g2d, 0, origin, myMinLabel, true);
    }
    if (myShowMax && myMaxLabel != null) {
      drawMarkerLabel(g2d, myAxisLength, origin, myMaxLabel, true);
    }

    Line2D.Float line = new Line2D.Float();

    // Draw minor ticks.
    for (int i = 0; i < myMinorMarkerPositions.size(); i++) {
      float scaledPosition = myMinorMarkerPositions.get(i) * myAxisLength;
      drawMarkerLine(g2d, line, scaledPosition, origin, myMinorMarkerLength);
    }

    // Draw major ticks.
    for (int i = 0; i < myMajorMarkerPositions.size(); i++) {
      float scaledPosition = myMajorMarkerPositions.get(i) * myAxisLength;
      drawMarkerLine(g2d, line, scaledPosition, origin, myMajorMarkerLength);
      drawMarkerLabel(g2d, scaledPosition, origin, myMarkerLabels.get(i), false);
    }
  }

  private void drawMarkerLine(Graphics2D g2d, Line2D.Float line, float markerOffset,
                              Point origin, int markerLength) {
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
    g2d.draw(line);
  }

  private void drawMarkerLabel(Graphics2D g2d, float markerOffset, Point origin, String value, boolean alwaysRender) {
    int stringAscent = myMetrics.getAscent();
    int stringLength = myMetrics.stringWidth(value);

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
      g2d.setColor(AdtUiUtils.DEFAULT_FONT_COLOR);
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

  public static class Builder {
    private static final int DEFAULT_MAJOR_MARKER_LENGTH = 10;
    private static final int DEFAULT_MINOR_MARKER_LENGTH = 4;

    // Required fields.
    @NotNull private final Range myRange;
    @NotNull private final BaseAxisFormatter myFormatter;
    @NotNull private final AxisOrientation myOrientation;

    // Optional/default fields.
    private Range myGlobalRange;
    private int myMajorMarkerLength = DEFAULT_MAJOR_MARKER_LENGTH;
    private int myMinorMarkerLength = DEFAULT_MINOR_MARKER_LENGTH;
    private int myStartMargin;
    private int myEndMargin;
    private boolean myShowMin;
    private boolean myShowMax;
    private boolean myShowUnitAtMax;
    private boolean myShowAxisLine = true;
    private boolean myClampToMajorTicks = false;
    private double myOffset = 0;
    @NotNull private String myLabel = "";
    @Nullable private AxisComponent myParentAxis;

    /**
     * @param range       a Range object this AxisComponent listens to for the min/max values.
     * @param formatter   formatter used for determining the tick marker and labels that need to be rendered.
     * @param orientation the orientation of the axis.
     */
    public Builder(@NotNull Range range, @NotNull BaseAxisFormatter formatter, @NotNull AxisOrientation orientation) {
      myRange = range;
      myFormatter = formatter;
      myOrientation = orientation;
    }

    /**
     * @param globalRange sets the global range on the AxisComponent
     * TODO this is only needed in the case of time axis, where the users can zoom in to a particular current range, but the Axis still
     * wants to use the global range as context when generating the marker labels. It would be nice if we can get rid of this extra
     * dependency.
     */
    public Builder setGlobalRange(@NotNull Range globalRange) {
      myGlobalRange = globalRange;
      return this;
    }

    /**
     * Sets the content of the axis' label.
     */
    public Builder setLabel(@NotNull String label) {
      myLabel = label;
      return this;
    }

    /**
     * @param parent sets the parent axis on the AxisComponent. e.g. the ticker marker spacing will lined up to the parent axis.
     */
    public Builder setParentAxis(@NotNull AxisComponent parent) {
      myParentAxis = parent;
      return this;
    }

    /**
     * Sets the lengths of the major and minor tick markers.
     */
    public Builder setMarkerLengths(int majorMarker, int minorMarker) {
      myMajorMarkerLength = majorMarker;
      myMinorMarkerLength = minorMarker;
      return this;
    }

    /**
     * Sets the start/end margin of the axis. These are required for the min/max label to not be clipped.
     */
    public Builder setMargins(int startMargin, int endMargin) {
      myStartMargin = startMargin;
      myEndMargin = endMargin;
      return this;
    }

    /**
     * Sets the offset to be used, by default offset is zero.
     * {@link AxisComponent#myOffset}
     */
    public Builder setOffset(double offset) {
      myOffset = offset;
      return this;
    }

    /**
     * @param showMin sets whether to render the min values.
     */
    public Builder showMin(boolean showMin) {
      myShowMin = showMin;
      return this;
    }

    /**
     * @param showMax sets whether to render the max values.
     */
    public Builder showMax(boolean showMax) {
      myShowMax = showMax;
      return this;
    }


    /**
     * @param showUnitAtMax if true, unit will only be shown at the max value.
     */
    public Builder showUnitAtMax(boolean showUnitAtMax) {
      myShowUnitAtMax = showUnitAtMax;
      return this;
    }

    /**
     * @param showAxisLine sets whether to display the axis guide line.
     */
    public Builder showAxisLine(boolean showAxisLine) {
      myShowAxisLine = showAxisLine;
      return this;
    }

    /**
     * @param clampToMajorTicks if true, the AxisComponent will extend itself to the next major tick based on the current max value.
     */
    public Builder clampToMajorTicks(boolean clampToMajorTicks) {
      myClampToMajorTicks = clampToMajorTicks;
      return this;
    }

    public AxisComponent build() {
      return new AxisComponent(this);
    }
  }
}