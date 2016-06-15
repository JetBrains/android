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
import com.android.tools.adtui.model.ReportingSeries;
import com.android.tools.adtui.model.ReportingSeriesRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * A component for performing/rendering selection and any overlay information (e.g. Tooltip).
 */
public final class SelectionComponent extends AnimatedComponent {

  /**
   * Percentage of the current range to apply on each zoom in/out operation.
   */
  public static final float ZOOM_FACTOR = 0.1f;

  /**
   * Minimum range the user can zoom into the profilers.
   */
  private static final double MINIMUM_VIEW_LENGTH_MS = TimeUnit.SECONDS.toMillis(1);

  /**
   * Default drawing Dimension for the handles.
   */
  private static final Dimension HANDLE_DIM = new Dimension(12, 40);
  private static final int DIM_ROUNDING_CORNER = 5;

  /**
   * Drawing parameters for the overlay info when user hovers over the charting components.
   */
  private static final int OVERLAY_INFO_PADDING = 5;
  private static final int OVERLAY_INFO_LINE_SPACING = 5;
  private static final int OVERLAY_INFO_COLUMN_SPACING = 10;
  private static final int OVERLAY_INFO_MIN_WIDTH = 200;
  private static final int OVERLAY_INFO_OFFSET = 10;
  private static final int OVERLAY_SHADOW_OFFSET = 2;
  private static final String OVERLAY_DRILL_DOWN_MESSAGE = "Double click to drill down";

  private enum Mode {
    // There are currently no selection.
    NO_SELECTION,
    // User is not modifying the selection but one exists.
    OBSERVE,
    // User is currently creating a selection. The min/max handles are created at the point
    // where the user clicks and the selection switches to ADJUST_MIN mode.
    CREATE,
    // User click+drag between the two handle and pressed the button. The selection range
    // shifts and the two handles move together as a block.
    MOVE,
    // User is adjusting the min.
    ADJUST_MIN,
    // User is adjusting the max.
    ADJUST_MAX
  }

  private Mode mode;

  @NotNull
  private final Component mHost;

  @NotNull
  private final AxisComponent mAxis;

  /**
   * The range being selected.
   */
  @NotNull
  private final Range mSelectionRange;

  /**
   * The global range for clamping selection.
   */
  @NotNull
  private final Range mDataRange;

  /**
   * The current viewing range which gets shifted when user drags the selection box beyond the
   * component's dimension.
   */
  @NotNull
  private final Range mViewRange;

  /**
   * Value used when moving the selection as a block: The user never click right in the middle of
   * the selection. This allows to move the block relative to the initial point the selection was
   * "grabbed".
   */
  private double mSelectionBlockClickOffset = 0;

  private boolean mZoomRequested;
  private double mZoomMinTarget;
  private double mZoomMaxTarget;

  /***
   * The container with series data to report. This should be the currently hovered charting component.
   */
  private ReportingSeriesRenderer mReportingContainer;

  @NotNull
  private final Map<ReportingSeries, Collection<ReportingSeries.ReportingData>> mReportingData;

  public SelectionComponent(@NotNull Component host,
                            @NotNull AxisComponent axis,
                            @NotNull Range selectionRange,
                            @NotNull Range dataRange,
                            @NotNull Range viewRange) {
    mHost = host;
    mAxis = axis;
    mDataRange = dataRange;
    mViewRange = viewRange;
    mode = Mode.NO_SELECTION;
    mSelectionRange = selectionRange;
    mReportingData = new HashMap<>();

    initListeners();
  }

  // TODO add logic to cancel selection by clicking
  // e.g.1 When the user presses once, after having a range selection the selection should deselect.
  // e.g.2 When the user moves the mouse after a point selection, after X seconds the selection should deselect
  private void initListeners() {
    mHost.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        // Just capture events of the left mouse button.
        if (e.getButton() != MouseEvent.BUTTON1) {
          return;
        }

        Point mousePosition = getMouseLocation();
        mode = getModeForMousePosition(mousePosition);
        switch (mode) {
          case NO_SELECTION:
          case CREATE:
            // TODO add delay before changing selection from a point to a range.
            double value = mAxis.getValueAtPosition(mousePosition.x);
            mSelectionRange.set(value, value);
            mode = Mode.ADJUST_MIN;
            break;
          default:
            break;
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        // Just capture events of the left mouse button.
        if (!(e.getButton() == MouseEvent.BUTTON1)) {
          return;
        }

        // Perform zooming to selection.
        if (e.isControlDown()) {
          requestZoom(mSelectionRange.getMin(), mSelectionRange.getMax());
        }

        mode = Mode.OBSERVE;
      }
    });

    mHost.addMouseWheelListener(e -> {
      double anchor = mAxis.getValueAtPosition(e.getPoint().x);
      float zoomPercentage = ZOOM_FACTOR * e.getWheelRotation();
      double delta = zoomPercentage * mViewRange.getLength();
      double minDelta = delta * (anchor - mViewRange.getMin()) / mViewRange.getLength();
      double maxDelta = delta - minDelta;
      requestZoom(mViewRange.getMin() - minDelta, mViewRange.getMax() + maxDelta);
    });
  }

  /**
   * Component.getMousePosition() returns null because SelectionComponent is usually overlayed.
   * The work around is to use absolute coordinates for mouse and component and subtract them.
   */
  private Point getMouseLocation() {
    Point mLoc = MouseInfo.getPointerInfo().getLocation();
    Point cLoc = getLocationOnScreen();
    int x = (int)(mLoc.getX() - cLoc.getX());
    int y = (int)(mLoc.getY() - cLoc.getY());
    return new Point(x, y);
  }

  private Mode getModeForMousePosition(Point mMousePosition) {
    // Detect when mouse is over a handle.
    if (getHandleAreaForValue(mSelectionRange.getMax()).contains(mMousePosition)) {
      return Mode.ADJUST_MAX;
    }

    // Detect when mouse is over the other handle.
    if (getHandleAreaForValue(mSelectionRange.getMin()).contains(mMousePosition)) {
      return Mode.ADJUST_MIN;
    }

    // Detect mouse between handle.
    if (getBetweenHandlesArea().contains(mMousePosition)) {
      saveMouseBlockOffset(mMousePosition);
      return Mode.MOVE;
    }
    return Mode.CREATE;
  }

  private void saveMouseBlockOffset(Point mMousePosition) {
    double value = mAxis.getValueAtPosition(mMousePosition.x);
    mSelectionBlockClickOffset = mSelectionRange.getMin() - value;
  }

  /**
   * Zoom by a percentage of the current view range using the center as the anchor
   */
  public void zoom(float percentage) {
    double zoomDelta = mViewRange.getLength() * percentage;
    requestZoom(mViewRange.getMin() - zoomDelta, mViewRange.getMax() + zoomDelta);
  }

  /*
   * Resets the view range to match the data range.
   * TODO this does not animate at the moment because we have a running mDataRange max value.
   */
  public void resetZoom() {
    mViewRange.set(mDataRange.getMin(), mDataRange.getMax());
  }

  public void clear() {
    mSelectionRange.set(0, 0);
    mode = Mode.NO_SELECTION;
  }

  @Override
  public void reset() {
    super.reset();
    clear();
  }

  @Override
  protected void updateData() {
    // Early return if the component is hidden.
    // TODO probably abstract the isShowing check to somewhere across all AnimatedComponents
    if (!isShowing()) {
      return;
    }

    if (mZoomRequested) {
      // TODO clamp zooming if a min range is reached.
      if (mZoomMinTarget != mViewRange.getMin() || mZoomMaxTarget != mViewRange.getMax()) {
        mViewRange.setTarget(mZoomMinTarget, mZoomMaxTarget);
        mViewRange.lockValues();
      }
      mZoomRequested = false;
    }

    Point mousePosition = getMouseLocation();
    double valueAtCursor = mAxis.getValueAtPosition(mousePosition.x);
    // Clamp to data range.
    valueAtCursor = mDataRange.clamp(valueAtCursor);

    // Gather any series data that need to be shown in the overlay.
    mReportingContainer = null;
    mReportingData.clear();
    // Convert mouse coordinates to mHost's coordinate space.
    mousePosition = SwingUtilities.convertPoint(this, mousePosition, mHost);
    Component hoveredComponent = SwingUtilities.getDeepestComponentAt(mHost, mousePosition.x, mousePosition.y);
    if (hoveredComponent instanceof ReportingSeriesRenderer) {
      mReportingContainer = (ReportingSeriesRenderer)hoveredComponent;
      for (ReportingSeries series : mReportingContainer.getReportingSeries()) {
        Collection<ReportingSeries.ReportingData> reportingDataCollection = series.getFullReportingData((long)valueAtCursor);
        for (ReportingSeries.ReportingData data : reportingDataCollection) {
          mReportingContainer.markData(data.timeStamp);
        }
        mReportingData.put(series, reportingDataCollection);
      }
    }

    // Early return if in observe mode and the selection has not changed.
    if (mode == Mode.OBSERVE || mode == Mode.NO_SELECTION) {
      return;
    }

    // Extend view range if necessary
    // If extended, lock range to force Scrollbar to quit STREAMING mode.
    if (valueAtCursor > mViewRange.getMax()) {
      mViewRange.setMax(valueAtCursor);
      mViewRange.lockValues();
    }
    else if (valueAtCursor < mViewRange.getMin()) {
      mViewRange.setMin(valueAtCursor);
      mViewRange.lockValues();
    }

    // Check if selection was inverted (min > max or max < min)
    if (mode == Mode.ADJUST_MIN && valueAtCursor > mSelectionRange.getMax()) {
      mSelectionRange.flip();
      mode = Mode.ADJUST_MAX;
    }
    else if (mode == Mode.ADJUST_MAX && valueAtCursor < mSelectionRange.getMin()) {
      mSelectionRange.flip();
      mode = Mode.ADJUST_MIN;
    }

    switch (mode) {
      case CREATE:
        break;
      case ADJUST_MIN:
        mSelectionRange.setMin(valueAtCursor);
        break;
      case ADJUST_MAX:
        mSelectionRange.setMax(valueAtCursor);
        break;
      case MOVE:
        double length = mSelectionRange.getLength();
        mSelectionRange.set(valueAtCursor + mSelectionBlockClickOffset,
                            valueAtCursor + mSelectionBlockClickOffset + length);

        // Limit the selection block to viewRange Min
        if (mSelectionRange.getMin() < mViewRange.getMin()) {
          mSelectionRange.shift(mViewRange.getMin() - mSelectionRange.getMin());
        }
        // Limit the selection block to viewRange Max
        if (mSelectionRange.getMax() > mViewRange.getMax()) {
          mSelectionRange.shift(mViewRange.getMax() - mSelectionRange.getMax());
        }
        break;
    }
  }

  private void drawCursor(Point position) {
    int cursor = Cursor.DEFAULT_CURSOR;

    if (getHandleAreaForValue(mSelectionRange.getMax()).contains(position) ||
        getHandleAreaForValue(mSelectionRange.getMin()).contains(position)) {
      // Detect mouse over the handles.
      // TODO: Replace with appropriate cursor.
      cursor = Cursor.MOVE_CURSOR;
    }
    else if (getBetweenHandlesArea().contains(position)) {
      // Detect mouse between handles
      cursor = Cursor.HAND_CURSOR;
    }

    if (getTopLevelAncestor().getCursor().getType() != cursor) {
      getTopLevelAncestor().setCursor(Cursor.getPredefinedCursor(cursor));
    }
  }

  /**
   * Manually draws a overlaying rectangle displaying rows of data corresponding to where
   * the user is currently pointing at.
   */
  private void drawOverlayInfo(Graphics2D g, Point position) {
    if (mReportingContainer == null) {
      return;
    }

    int ascent = mDefaultFontMetrics.getAscent();
    int labelColumnWidth = 0;
    int dataColumnWidth = 0;
    int overlayHeight = OVERLAY_INFO_PADDING * 2 +  // top + bottom padding
                        ascent + OVERLAY_INFO_LINE_SPACING;  // spacing for default double-click message.

    String containerName = mReportingContainer.getContainerName();
    if (containerName != null) {
      labelColumnWidth = mDefaultFontMetrics.stringWidth(containerName);
      overlayHeight += ascent + OVERLAY_INFO_LINE_SPACING;
    }

    // First pass through the data to measure the necessary width and height of the background rectangle.
    ArrayDeque<Integer> dataWidthArray = new ArrayDeque<>();
    for (ReportingSeries series : mReportingData.keySet()) {
      int labelWidth = mDefaultFontMetrics.stringWidth(series.getLabel());
      labelColumnWidth = Math.max(labelColumnWidth, labelWidth);
      for (ReportingSeries.ReportingData data : mReportingData.get(series)) {
        int dataWidth = mDefaultFontMetrics.stringWidth(data.formattedYData);
        dataColumnWidth = Math.max(dataColumnWidth, dataWidth);
        dataWidthArray.add(dataWidth);
        overlayHeight += ascent + OVERLAY_INFO_LINE_SPACING;
      }
    }

    int overlayWidth = Math.max(OVERLAY_INFO_MIN_WIDTH,
                                // Account for padding on both sides and the spacing between the label and data columns.
                                OVERLAY_INFO_PADDING * 2 + OVERLAY_INFO_COLUMN_SPACING + labelColumnWidth + dataColumnWidth);

    // TODO adjust placement position if we are out of space to the right.
    Rectangle2D.Float rect = new Rectangle2D.Float(0, 0, overlayWidth, overlayHeight);
    g.translate(position.x + OVERLAY_INFO_OFFSET, position.y + OVERLAY_INFO_OFFSET);
    g.translate(OVERLAY_SHADOW_OFFSET, OVERLAY_SHADOW_OFFSET);
    g.setColor(AdtUiUtils.DEFAULT_FONT_COLOR);
    g.fill(rect); // drop shadow
    g.translate(-OVERLAY_SHADOW_OFFSET, -OVERLAY_SHADOW_OFFSET);
    g.setColor(AdtUiUtils.OVERLAY_INFO_BACKGROUND);
    g.fill(rect); // overlay window surface.

    // Second pass through the data to draw the individual texts.
    g.setColor(AdtUiUtils.DEFAULT_FONT_COLOR);
    g.setFont(AdtUiUtils.DEFAULT_FONT);
    int textHeight = OVERLAY_INFO_PADDING;
    if (containerName != null) {
      textHeight += ascent;
      g.drawString(containerName, OVERLAY_INFO_PADDING, textHeight);
      textHeight += OVERLAY_INFO_LINE_SPACING;
    }

    for (ReportingSeries series : mReportingData.keySet()) {
      for (ReportingSeries.ReportingData data : mReportingData.get(series)) {
        textHeight += ascent;
        g.drawString(series.getLabel(), OVERLAY_INFO_PADDING, textHeight);
        g.drawString(data.formattedYData, overlayWidth - OVERLAY_INFO_PADDING - dataWidthArray.remove(), textHeight);
        textHeight += OVERLAY_INFO_LINE_SPACING;
      }
    }

    // Draw separator and double-click instruction message.
    g.drawLine(0, textHeight, overlayWidth, textHeight);
    textHeight += OVERLAY_INFO_LINE_SPACING + ascent;
    g.drawString(OVERLAY_DRILL_DOWN_MESSAGE, OVERLAY_INFO_PADDING, textHeight);

    // Reset transform.
    g.translate(-(position.x + OVERLAY_INFO_OFFSET), -(position.y + OVERLAY_INFO_OFFSET));
  }

  @Override
  protected void draw(Graphics2D g) {
    Dimension dim = getSize();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Point mousePosition = getMouseLocation();

    // Draw selection indicators if a selection exists.
    if (mode != Mode.NO_SELECTION) {
      drawCursor(mousePosition);

      // Draw selected area.
      g.setColor(AdtUiUtils.SELECTION_BACKGROUND);
      float startXPos = mAxis.getPositionAtValue(mSelectionRange.getMin());
      float endXPos = mAxis.getPositionAtValue(mSelectionRange.getMax());
      Rectangle2D.Float rect = new Rectangle2D.Float(startXPos, 0, endXPos - startXPos, dim.height);
      g.fill(rect);

      // Draw vertical lines, one for each endsValue.
      g.setColor(AdtUiUtils.SELECTION_FOREGROUND);
      Path2D.Float path = new Path2D.Float();
      path.moveTo(startXPos, 0);
      path.lineTo(startXPos, dim.height);
      path.moveTo(endXPos, dim.height);
      path.lineTo(endXPos, 0);
      g.draw(path);

      // Draw handles
      drawHandleAtValue(g, mSelectionRange.getMin());
      drawHandleAtValue(g, mSelectionRange.getMax());
    }

    drawOverlayInfo(g, mousePosition);
  }

  private void requestZoom(double minTarget, double maxTarget) {
    mZoomMinTarget = Math.max(mDataRange.getMin(), minTarget);
    mZoomMaxTarget = Math.min(mDataRange.getMax(), maxTarget);

    // Clamp zoom to minimum range by distributing the delta evenly between the min and max.
    double zoomLength = mZoomMaxTarget - mZoomMinTarget;
    if (zoomLength < MINIMUM_VIEW_LENGTH_MS) {
      double delta = (MINIMUM_VIEW_LENGTH_MS - zoomLength) / 2;
      double clampedZoomMin = Math.max(mDataRange.getMin(), mZoomMinTarget - delta);
      double clampedZoomMax = Math.min(mDataRange.getMax(), mZoomMaxTarget + delta);

      // Distribute any extra delta to the other side if the clamped zooms were bounded by mDataRange.
      if (mZoomMinTarget - clampedZoomMin < delta) {
        clampedZoomMax += delta - (mZoomMinTarget - clampedZoomMin);
      }
      else if (clampedZoomMax - mZoomMaxTarget < delta) {
        clampedZoomMin -= delta - (clampedZoomMax - mZoomMaxTarget);
      }

      mZoomMinTarget = clampedZoomMin;
      mZoomMaxTarget = clampedZoomMax;
    }

    mZoomRequested = true;
  }

  private void drawHandleAtValue(Graphics2D g, double value) {
    g.setPaint(AdtUiUtils.SELECTION_HANDLE);
    RoundRectangle2D.Double handle = getHandleAreaForValue(value);
    g.fill(handle);
  }

  private RoundRectangle2D.Double getHandleAreaForValue(double value) {
    float x = mAxis.getPositionAtValue(value);
    return new RoundRectangle2D.Double(x - HANDLE_DIM.getWidth() / 2, 0, HANDLE_DIM.getWidth(),
                                       HANDLE_DIM.getHeight(), DIM_ROUNDING_CORNER, DIM_ROUNDING_CORNER);
  }

  private Rectangle2D.Double getBetweenHandlesArea() {
    // Convert range space value to component space value.
    double startXPos = mAxis.getPositionAtValue(mSelectionRange.getMin());
    double endXPos = mAxis.getPositionAtValue(mSelectionRange.getMax());

    return new Rectangle2D.Double(startXPos - HANDLE_DIM.getWidth() / 2, 0, endXPos - startXPos,
                                  HANDLE_DIM.getHeight());
  }
}
