/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.adtui.chart.statechart;

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.Stopwatch;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.MouseEventHandler;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A chart component that renders series of state change events as rectangles.
 */
public final class StateChart<T> extends AnimatedComponent {
  private static final int INVALID_INDEX = -1;

  public enum RenderMode {
    BAR,  // Each state is rendered as a filled rectangle until the next state changed.
    TEXT  // Each state is marked with a vertical line and and corresponding state text/label at the beginning.
  }

  private static final int TEXT_PADDING = 3;

  private StateChartModel<T> myModel;

  /**
   * An object that maps between a type T, and a color to be used in the StateChart, all values of T should return a valid color.
   */
  @NotNull
  private final StateChartColorProvider<T> myColorProvider;

  private float myHeightGap;

  @NotNull
  private RenderMode myRenderMode;

  @NotNull
  private final StateChartConfig<T> myConfig;

  private boolean myNeedsTransformToViewSpace;

  @NotNull
  private final StateChartTextConverter<T> myTextConverter;

  private final List<Rectangle2D.Float> myRectangles = new ArrayList<>();
  private final List<T> myRectangleValues = new ArrayList<>();

  /**
   * In some cases, StateChart is delegated to by a parent containing component (e.g. a JList or
   * a table). In order to preform some painting optimizations, we need access to that source
   * component.
   *
   * TODO(b/116747281): It seems like we shouldn't have to know about this. Otherwise, almost
   * every component would need special-case logic like this. We should revisit how this class
   * is being used by CpuCellRenderer.
   */
  @Nullable
  private Object myMouseEventSource = null;

  @Nullable
  private Point myMousePoint = null;

  @Nullable
  private Point myRowPoint = null;

  private int myRowIndex = INVALID_INDEX;

  /**
   * @param colors map of a state to corresponding color
   */
  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<T> model, @NotNull Map<T, Color> colors) {
    this(model, new StateChartColorProvider<T>() {
      @Override
      @NotNull
      public Color getColor(boolean isMouseOver, @NotNull T value) {
        Color color = colors.get(value);
        return isMouseOver ? ColorUtil.brighter(color, 2) : color;
      }
    });
  }

  public StateChart(@NotNull StateChartModel<T> model, @NotNull StateChartColorProvider<T> colorMapping) {
    this(model, new StateChartConfig<>(new DefaultStateChartReducer<>()), colorMapping, (val) -> val.toString());
  }

  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartColorProvider<T> colorMapping,
                    StateChartTextConverter<T> textConverter) {
    this(model, new StateChartConfig<>(new DefaultStateChartReducer<>()), colorMapping, textConverter);
  }

  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartConfig<T> config,
                    @NotNull StateChartColorProvider<T> colorMapping) {
    this(model, config, colorMapping, (val) -> val.toString());
  }

  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartConfig<T> config,
                    @NotNull StateChartColorProvider<T> colorMapping,
                    @NotNull StateChartTextConverter<T> textConverter) {
    myColorProvider = colorMapping;
    myRenderMode = RenderMode.BAR;
    myConfig = config;
    myNeedsTransformToViewSpace = true;
    myTextConverter = textConverter;
    setFont(AdtUiUtils.DEFAULT_FONT);
    setModel(model);
    setHeightGap(myConfig.getHeightGap());
    registerMouseEvents();
  }

  public void setModel(@NotNull StateChartModel<T> model) {
    if (myModel != null) {
      myModel.removeDependencies(myAspectObserver);
    }
    myModel = model;
    myModel.addDependency(myAspectObserver).onChange(StateChartModel.Aspect.MODEL_CHANGED, this::modelChanged);
    modelChanged();
  }

  private void modelChanged() {
    myNeedsTransformToViewSpace = true;
    opaqueRepaint();
  }

  public void setRenderMode(@NotNull RenderMode mode) {
    myRenderMode = mode;
  }

  /**
   * Sets the gap between multiple data series.
   *
   * @param gap The gap value as a percentage {0...1} of the height given to each data series
   */
  public void setHeightGap(float gap) {
    myHeightGap = gap;
  }

  private void clearRectangles() {
    myRectangles.clear();
    myRectangleValues.clear();
  }

  /**
   * Creates a rectangle with the supplied dimensions. This function will normalize the x and width values.
   *
   * @param value     value used to associate with the created rectangle..
   * @param previousX value used to determine the x position and width of the rectangle. This value should be relative to the currentX param.
   * @param currentX  value used to determine the width of the rectangle. This value should be relative to the previousX param.
   * @param minX      minimum value of the range total range used to normalize the x position and width of the rectangle.
   * @param invRange  inverse of total range used to normalize the x position and width of the rectangle (~7% gain in performance).
   * @param rectY     rectangle height offset from max growth of rectangle. This value is expressed as a percentage from 0-1
   * @param height    height of rectangle
   */
  private void addRectangleDelta(@NotNull T value,
                                 double previousX,
                                 double currentX,
                                 double minX,
                                 double invRange,
                                 float rectY,
                                 float height) {
    // Because we start our activity line from the bottom and grow up we offset the height from the bottom of the component
    // instead of the top by subtracting our height from 1.
    Rectangle2D.Float rect = new Rectangle2D.Float(
      (float)((previousX - minX) * invRange),
      rectY,
      (float)((currentX - previousX) * invRange),
      height);
    myRectangles.add(rect);
    myRectangleValues.add(value);
  }

  private void transformToViewSpace() {
    if (!myNeedsTransformToViewSpace) {
      return;
    }

    myNeedsTransformToViewSpace = false;

    List<RangedSeries<T>> series = myModel.getSeries();
    int seriesSize = series.size();
    if (seriesSize == 0) {
      return;
    }

    // TODO support interpolation.
    float rectHeight = 1.0f / seriesSize;
    float gap = rectHeight * myHeightGap;
    float barHeight = rectHeight - gap;

    clearRectangles();

    for (int seriesIndex = 0; seriesIndex < seriesSize; seriesIndex++) {
      RangedSeries<T> data = series.get(seriesIndex);

      final double min = data.getXRange().getMin();
      final double max = data.getXRange().getMax();
      final double invRange = 1.0 / (max - min);
      float startHeight = 1.0f - (rectHeight * (seriesIndex + 1));

      List<SeriesData<T>> seriesDataList = data.getSeries();
      if (seriesDataList.isEmpty()) {
        continue;
      }

      // Construct rectangles.
      long previousX = seriesDataList.get(0).x;
      T previousValue = seriesDataList.get(0).value;
      for (int i = 1; i < seriesDataList.size(); i++) {
        SeriesData<T> seriesData = seriesDataList.get(i);
        long x = seriesData.x;
        T value = seriesData.value;

        if (value.equals(previousValue)) {
          // Ignore repeated values.
          continue;
        }

        assert previousValue != null;

        // Don't draw if this block doesn't intersect with [min..max]
        if (x >= min) {
          // Draw the previous block.
          addRectangleDelta(previousValue, Math.max(min, previousX), Math.min(max, x), min, invRange, startHeight + gap * 0.5f, barHeight);
        }

        // Start a new block.
        previousValue = value;
        previousX = x;

        if (previousX >= max) {
          // Drawn past max range, stop.
          break;
        }
      }
      // The last data point continues till max
      if (previousX < max && previousValue != null) {
        addRectangleDelta(previousValue, Math.max(min, previousX), max, min, invRange, startHeight + gap * 0.5f, barHeight);
      }
    }
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    Stopwatch stopwatch = new Stopwatch().start();

    transformToViewSpace();

    long transformTime = stopwatch.getElapsedSinceLastDeltaNs();

    g2d.setFont(getFont());
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

    assert myRectangles.size() == myRectangleValues.size();
    List<Rectangle2D.Float> transformedShapes = new ArrayList<>(myRectangles.size());
    List<T> transformedValues = new ArrayList<>(myRectangleValues.size());

    float scaleX = (float)getWidth();
    float scaleY = (float)getHeight();

    Rectangle clipRect = g2d.getClipBounds();
    int startIndexInclusive = 0;
    int endIndexExclusive = myRectangles.size();
    if (clipRect != null) {
      if (clipRect.x != 0) {
        startIndexInclusive = Collections.binarySearch(
          myRectangles,
          new Rectangle2D.Float(clipRect.x / scaleX, 0, 0, 0),
          (value, key) -> (value.x + value.width < key.x) ? -1 : (value.x > key.x ? 1 : 0));
        if (startIndexInclusive < 0) {
          startIndexInclusive = -(startIndexInclusive + 1);
        }
      }
      if (clipRect.width != getWidth()) {
        endIndexExclusive = Collections.binarySearch(
          myRectangles,
          new Rectangle2D.Float((clipRect.x + clipRect.width) / scaleX, 0, 0, 0),
          (value, key) -> (value.x + value.width < key.x) ? -1 : (value.x > key.x ? 1 : 0));
        if (endIndexExclusive < 0) {
          endIndexExclusive = -(endIndexExclusive + 1);
        }
        else {
          // We need to increment since if the result from the binary search is positive,
          // then it's an index of a rectangle that we actually want to render and not skip/terminate the transform loop below.
          endIndexExclusive++;
        }
      }
    }

    for (int i = startIndexInclusive; i < endIndexExclusive; i++) {
      Rectangle2D.Float rectangle = myRectangles.get(i);
      // Manually scaling the rectangle results in ~6x performance improvement over calling
      // AffineTransform::createTransformedShape. The reason for this is the shape created is a Point2D.Double.
      // This shape has to support all types of points as such cannot be transformed as efficiently as a
      // rectangle. Furthermore, AffineTransform uses doubles, which is about half as fast for LS
      // when compared to floats (doubles memory bandwidth).
      transformedShapes.add(new Rectangle2D.Float(rectangle.x * scaleX,
                                                  rectangle.y * scaleY,
                                                  rectangle.width * scaleX,
                                                  rectangle.height * scaleY));
      transformedValues.add(myRectangleValues.get(i));
    }

    long scalingTime = stopwatch.getElapsedSinceLastDeltaNs();

    myConfig.getReducer().reduce(transformedShapes, transformedValues);
    assert transformedShapes.size() == transformedValues.size();

    long reducerTime = stopwatch.getElapsedSinceLastDeltaNs();
    int hoverIndex = INVALID_INDEX;
    if (myRowPoint != null) {
      float mouseXFloat = (float)myRowPoint.x;
      hoverIndex = Collections.binarySearch(
        transformedShapes,
        // Optimization: Encode mouseXFloat into width component of the key to avoid recalculating it on every invocation of the Comparable.
        new Rectangle2D.Float(mouseXFloat, 0, mouseXFloat + 1.0f, 0),
        (value, key) -> (value.x + value.width < key.x) ? -1 : (value.x > key.width ? 1 : 0));
    }

    for (int i = 0; i < transformedShapes.size(); i++) {
      T value = transformedValues.get(i);
      Rectangle2D.Float rect = transformedShapes.get(i);
      boolean isMouseOver = (i == hoverIndex);
      Color color = myColorProvider.getColor(isMouseOver, value);
      g2d.setColor(color);
      g2d.fill(rect);
      if (myRenderMode == RenderMode.TEXT) {
        String valueText = myTextConverter.convertToString(value);
        String text = AdtUiUtils.shrinkToFit(valueText, mDefaultFontMetrics, rect.width - TEXT_PADDING * 2);
        if (!text.isEmpty()) {
          g2d.setColor(myColorProvider.getFontColor(isMouseOver, value));
          float textOffset = rect.y + (rect.height - mDefaultFontMetrics.getHeight()) * 0.5f;
          textOffset += mDefaultFontMetrics.getAscent();
          g2d.drawString(text, rect.x + TEXT_PADDING, textOffset);
        }
      }
    }

    long drawTime = stopwatch.getElapsedSinceLastDeltaNs();

    addDebugInfo("XS ms: %.2fms, %.2fms", transformTime / 1000000.f, scalingTime / 1000000.f);
    addDebugInfo("RDT ms: %.2f, %.2f, %.2f", reducerTime / 1000000.f, drawTime / 1000000.f,
                 (scalingTime + reducerTime + drawTime) / 1000000.f);
    addDebugInfo("# of drawn rects: %d", transformedShapes.size());
  }

  private void registerMouseEvents() {
    MouseEventHandler handler = new MouseEventHandler() {
      @Override
      protected void handle(MouseEvent event) {
        if (event.getPoint().equals(myMousePoint)) {
          return;
        }

        if (myRowIndex != INVALID_INDEX) {
          Point oldRowOriginInEventSpace = new Point(0, 0);
          if (event.getSource() instanceof JList) {
            JList sourceList = (JList)event.getSource();
            // First convert the event mouse position into row index for the list.
            oldRowOriginInEventSpace = sourceList.getUI().indexToLocation(sourceList, myRowIndex);
          }

          if (oldRowOriginInEventSpace != null) {
            renderUnion(oldRowOriginInEventSpace);
          }
        }

        if (event.getID() == MouseEvent.MOUSE_EXITED) {
          myMousePoint = null;
          myRowPoint = null;
          myMouseEventSource = null;
          myRowIndex = INVALID_INDEX;
        }
        else {
          Point rowOrigin = new Point(0, 0);
          if (event.getSource() instanceof JList) {
            // Since JList uses CellRenderers to render each list item, we actually need to translate the source location (in the JList's
            // space) to the cell's coordinate space. We do this by simply getting the row index that the mouse location corresponds to,
            // and then translate the index back to the List's coordinate space (which uses the origin of the row automatically). Then we
            // subtract/translate the mouse point (which is still in the JLists's space) by the origin to get the mouse coordinate in the
            // row's origin. This is akin to calculating the value after the decimal of a floating point number to its floor.
            JList sourceList = (JList)event.getSource();
            myRowIndex = sourceList.getUI().locationToIndex(sourceList, event.getPoint());
            if (myRowIndex >= 0) {
              rowOrigin = sourceList.getUI().indexToLocation(sourceList, myRowIndex);
              // locationToIndex above implies indexToLocation call will always be valid
              assert rowOrigin != null;
            }
            myMousePoint = event.getPoint();
            myRowPoint = new Point(myMousePoint);
            myRowPoint.translate(-rowOrigin.x, -rowOrigin.y);
          }
          else {
            myMousePoint = event.getPoint();
            myRowPoint = myMousePoint;
            // If the StateChart is not in a JList, then there is only one row. So we set the row to the first (and only) row to let the
            // render happen.
            myRowIndex = 0;
          }
          myMouseEventSource = event.getSource();

          if (myRowIndex != INVALID_INDEX) {
            renderUnion(rowOrigin);
          }
        }
      }
    };
    addMouseListener(handler);
    addMouseMotionListener(handler);
  }

  private void renderUnion(@NotNull Point containerOffset) {
    if (myRowPoint != null && myMouseEventSource instanceof Component) {
      Rectangle2D.Float union = getMouseRectanglesUnion(myRowPoint);
      if (union != null) {
        // StateChart is commonly used as a cell renderer component, and therefore is not in the proper Swing hierarchy.
        // Because of this, we need to use the source (which is probably a JList) to perform the actual repaint.
        ((Component)myMouseEventSource)
          .repaint((int)union.x + containerOffset.x, (int)union.y + containerOffset.y, (int)Math.ceil(union.width),
                   (int)Math.ceil(union.height));
      }
    }
  }

  @Nullable
  private Rectangle2D.Float getMouseRectanglesUnion(@NotNull Point mousePoint) {
    List<RangedSeries<T>> series = myModel.getSeries();
    int seriesSize = series.size();
    if (seriesSize == 0) {
      return null;
    }

    double scaleX = (float)getWidth();
    double scaleY = (float)getHeight();

    final double normalizedMouseY = 1.0f - (float)mousePoint.y / scaleY;
    // Use min just in case of Swing off-by-one-pixel-mouse-handling issues.
    int seriesIndex = Math.min(seriesSize - 1, (int)(normalizedMouseY * seriesSize));
    if (seriesIndex < 0 || seriesIndex >= series.size()) {
      Logger.getInstance(StateChart.class).warn(
        String.format(Locale.US,
                      "Series index in getMouseRectanglesUnion is out of bounds: mouseY = %d, scaleY = %f",
                      mousePoint.y,
                      scaleY));
      return new Rectangle2D.Float(0, 0, (float)scaleX, (float)scaleY);
    }

    RangedSeries<T> data = series.get(seriesIndex);
    final double min = data.getXRange().getMin();
    final double max = data.getXRange().getMax();
    final double range = max - min;

    // Convert mouseX into data/series coordinate space. However, note that the mouse covers a whole pixel, which has width.
    // Therefore, we need to find all the rectangles potentially intersecting the pixel. We start by looking for rectangles that
    // intersect the left side of the pixel.
    final double mouseXDouble = (double)mousePoint.x;
    final double modelMouseXLeft = mouseXDouble / scaleX * range + min;
    List<SeriesData<T>> seriesDataList = data.getSeries();
    if (seriesDataList.isEmpty()) {
      return null;
    }

    int rectangleLeftIndex = Collections.binarySearch(
      seriesDataList, new SeriesData<T>((long)modelMouseXLeft, null), (value, key) -> (int)(value.x - key.x));

    boolean isInsertionOnLeftX = false;
    if (rectangleLeftIndex < 0) {
      // Convert back to a positive index if we have an insertion index.
      rectangleLeftIndex = -(rectangleLeftIndex + 1);
      isInsertionOnLeftX = true;
    }
    // We may have an insertion past the end of the list, so we clamp the index.
    rectangleLeftIndex = Math.min(rectangleLeftIndex, seriesDataList.size() - 1);
    // We most likely won't have a data item whose x exactly corresponds to where the mouse is, therefore we scan back in time to the
    // first item that is prior to what our mouse maps to.
    if (isInsertionOnLeftX) {
      // If we're in the rectangleLeftIndex < 0 block, it means the insertion point is at 0. Therefore we only check when the insertion
      // point is non-0 (> 0, after the negation conversion).
      while (rectangleLeftIndex > 0 && seriesDataList.get(rectangleLeftIndex).x > modelMouseXLeft) {
        rectangleLeftIndex--;
      }
    }

    // Then search to the right of rectangleLeftIndex to fill-find all events under the pixel.
    final long modelMouseXRight = (long)Math.ceil((mouseXDouble + 1.0) / scaleX * range + min);
    int rectangleRightIndex = rectangleLeftIndex; // rectangleRightIndex is exclusive.
    for (int i = rectangleLeftIndex + 1; i < seriesDataList.size(); i++) {
      if (seriesDataList.get(i).x <= modelMouseXRight) {
        rectangleRightIndex = i;
      }
      else {
        break;
      }
    }
    if (rectangleRightIndex < seriesDataList.size()) {
      // Now expand the selection for all duplicated values from the right index.
      final T lastValue = seriesDataList.get(rectangleRightIndex).value;
      for (rectangleRightIndex += 1;
        // We'll allow rectangleRightIndex to get incremented to one past the end/last equal value since it's an exclusive index.
           rectangleRightIndex < seriesDataList.size() && seriesDataList.get(rectangleRightIndex).value.equals(lastValue);
           rectangleRightIndex++) {
        // Do nothing, the checks and increment index in the for loop do all the work.
      }
    }

    // Now find the type of of the left index, and search left for first occurrence of this value.
    final T firstValue = seriesDataList.get(rectangleLeftIndex).value;
    for (int i = rectangleLeftIndex - 1; i >= 0; i--) {
      if (seriesDataList.get(i).value == firstValue) {
        rectangleLeftIndex = i;
      }
      else {
        break;
      }
    }

    // Now transform the union of the left and right (or range max) index x values back into view space.
    final double modelXLeft = Math.max(min, seriesDataList.get(rectangleLeftIndex).x);
    // Remember that rectangleRightIndex is exclusive.
    final double modelXRight =
      Math.min(max, rectangleRightIndex >= seriesDataList.size() ? max : seriesDataList.get(rectangleRightIndex).x);

    final double screenXLeft = (modelXLeft - min) * scaleX / range;
    final double screenXRight = (modelXRight - min) * scaleX / range;
    final double screenYTop = (double)seriesIndex * scaleY / (double)seriesSize;
    final double screenYBottom = (double)(seriesIndex + 1) * scaleY / (double)seriesSize;

    final double screenXLeftFloor = Math.floor(screenXLeft);
    final double screenYTopCeil = Math.ceil(screenYTop);
    final double screenWidth = Math.ceil(screenXRight) - screenXLeftFloor;
    final double screenHeight = Math.floor(screenYBottom) - screenYTopCeil;

    return new Rectangle2D.Float((float)screenXLeftFloor, (float)screenYTopCeil, (float)screenWidth, (float)screenHeight);
  }
}
