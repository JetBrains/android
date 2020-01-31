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
package com.android.tools.adtui.chart.linechart;

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A custom renderer to support drawing {@link DurationData} over line charts
 */
public final class DurationDataRenderer<E extends DurationData> extends AspectObserver implements LineChartCustomRenderer {

  static final float EPSILON = 1e-6f;

  @NotNull private DurationDataModel<E> myModel;

  /**
   * Cached rectangles calculated during updateData used for detecting if a DurationData label has been clicked on.
   * Note that the x+y values (unknown actual component dimension at updateData time) stored are normalized,
   * but the width+height values (predetermined by the icon+label dimensions) are not.
   * Note that {@link #myClickRegionCache} is 1:1 with {@link #myDataCache}.
   */
  @NotNull private final List<Rectangle2D.Float> myClickRegionCache = new ArrayList<>();
  /**
   * Note that {@link #myDataCache} is 1:1 with {@link #myClickRegionCache}.
   */
  @NotNull private final List<SeriesData<E>> myDataCache = new ArrayList<>();

  @NotNull private final Color myColor;
  @Nullable private final Color myDurationBgColor;

  @Nullable private Icon myIcon;
  @Nullable private Function<E, Icon> myIconMapper;
  @Nullable private Stroke myStroke;
  @Nullable private Function<E, String> myLabelProvider;
  @Nullable private Consumer<E> myClickHandler;
  @Nullable private Consumer<E> myHoverHandler;
  @Nullable private Color myLabelBgColor;
  @Nullable private Color myLabelHoveredBgColor;
  @Nullable private Color myLabelClickedBgColor;
  @Nullable private Color myLabelTextColor;
  private float myLineStrokeOffset;
  private float myLabelXOffset;
  private float myLabelYOffset;
  @NotNull private final Insets myHostInsets;
  /**
   * Percentage of screen dimension the icon+label for the DurationData will be offset. Initial values are defaults.
   */
  private int myClickRegionPaddingX;
  private int myClickRegionPaddingY;

  @NotNull private final List<Rectangle2D.Float> myPathCache = new ArrayList<>();
  @NotNull private final List<JLabel> myLabelCache = new ArrayList<>();

  @NotNull private final Map<RangedContinuousSeries, LineConfig> myCustomLineConfigs = new HashMap<>();

  private Point myMousePosition;
  private Component myMouseComponent;
  private boolean myClick;
  private boolean myInComponentRegion;

  public DurationDataRenderer(@NotNull DurationDataModel<E> model, @NotNull Builder<E> builder) {
    myModel = model;
    myColor = builder.myColor;
    myDurationBgColor = builder.myDurationBgColor;
    myIcon = builder.myIcon;
    myIconMapper = builder.myIconMapper;
    myStroke = builder.myStroke;
    myLabelProvider = builder.myLabelProvider;
    myClickHandler = builder.myClickHandler;
    myHoverHandler = builder.myHoverHandler;
    myLabelBgColor = builder.myLabelBgColor;
    myLabelHoveredBgColor = builder.myLabelHoveredBgColor;
    myLabelClickedBgColor = builder.myLabelClickedBgColor;
    myLabelTextColor = builder.myLabelTextColor;
    myLabelXOffset = builder.myLabelXOffset;
    myLabelYOffset = builder.myLabelYOffset;
    myHostInsets = builder.myHostInsets;
    myClickRegionPaddingX = builder.myClickRegionPaddingX;
    myClickRegionPaddingY = builder.myClickRegionPaddingY;
    if (myStroke instanceof BasicStroke) {
      BasicStroke stroke = (BasicStroke)myStroke;
      myLineStrokeOffset = stroke.getLineWidth() / 2f;
    }
    else {
      myLineStrokeOffset = 0;
    }

    myModel.addDependency(this).onChange(DurationDataModel.Aspect.DURATION_DATA, this::modelChanged);
  }

  public void addCustomLineConfig(@NotNull RangedContinuousSeries series, @NotNull LineConfig config) {
    myCustomLineConfigs.put(series, config);
  }

  @VisibleForTesting
  LineConfig getCustomLineConfig(@NotNull RangedContinuousSeries series) {
    return myCustomLineConfigs.get(series);
  }

  @VisibleForTesting
  @NotNull
  public List<Rectangle2D.Float> getClickRegionCache() {
    return myClickRegionCache;
  }

  private void modelChanged() {
    // Generate the rectangle regions for the duration data series
    myDataCache.clear();
    myClickRegionCache.clear();
    myPathCache.clear();
    myLabelCache.clear();

    RangedSeries<E> series = myModel.getSeries();
    RangedContinuousSeries attached = myModel.getAttachedSeries();
    Predicate<SeriesData<E>> attachedPredicate = myModel.getAttachPredicate();
    double xMin = series.getXRange().getMin();
    double xLength = series.getXRange().getLength();
    List<SeriesData<E>> seriesList = series.getSeries();
    List<SeriesData<Long>> attachedSeriesList = attached != null ? attached.getSeries() : null;
    double yMin = attached == null ? 0.0 : attached.getYRange().getMin(); // TODO What happens if yMax - yMin == 0?
    double yMax = attached == null ? 0.0 : attached.getYRange().getMax();

    int j = 0;
    SeriesData<Long> lastFoundData = null;
    for (SeriesData<E> data : seriesList) {
      Rectangle2D.Float rect = new Rectangle2D.Float();
      double yStart = 1;
      double xStart = (data.x - xMin) / xLength;
      double xDuration = data.value.getDurationUs() / xLength;
      rect.setRect(xStart, 0, xDuration, 1);
      myPathCache.add(rect);

      Rectangle2D.Float clickRegion = new Rectangle2D.Float();
      myDataCache.add(data);
      myClickRegionCache.add(clickRegion);
      // If the DurationData needs to attach to a line series, finds the Y value on the line series matching the current DurationData.
      // This will be used as the y position to draw the icon +/ label.
      if (attachedSeriesList != null) {
        if (attachedPredicate == null || attachedPredicate.test(data)) {
          for (; j < attachedSeriesList.size(); j++) {
            SeriesData<Long> seriesData = attachedSeriesList.get(j);
            if (seriesData.x - data.x > EPSILON) {
              // Stop as soon as we found a point on the attached series greater than the duration data's start point.
              if (lastFoundData == null) {
                // If the duration data is before the first data point on the attached series, simply places the DurationData
                // at the bottom (yStart == 1), as we have nothing to attach to.
                break;
              }
              // Interpolate the y value in case the attached series and the duration data series do not match.
              assert myModel.getInterpolatable() != null;
              double adjustedY = myModel.getInterpolatable().interpolate(lastFoundData, seriesData, data.x);
              yStart = 1 - (adjustedY - yMin) / (yMax - yMin);
              break;
            }
            else if (j == attachedSeriesList.size() - 1) {
              // The duration data is after the last data point on the attached series. We assume the lastFoundData to continue to extend
              // indefinitely, so place the DurationData at that data's y value.
              yStart = 1 - (seriesData.value - yMin) / (yMax - yMin);
            }
            lastFoundData = seriesData;
          }
        }
      }

      double regionWidth = 0;
      double regionHeight = 0;
      Icon icon = getIcon(data.value);
      if (icon != null) {
        regionWidth += icon.getIconWidth();
        regionHeight += icon.getIconHeight();
      }

      if (myLabelProvider != null) {
        JLabel label = new JLabel(myLabelProvider.apply(data.value));
        label.setFont(AdtUiUtils.DEFAULT_FONT.deriveFont(9f));
        label.setForeground(myLabelTextColor);
        Dimension size = label.getPreferredSize();
        label.setBounds(0, 0, size.width, size.height);
        myLabelCache.add(label);

        regionWidth += size.getWidth(); // TODO padding between label + icon?
        regionHeight = Math.max(regionHeight, size.getHeight());
      }

      if (regionWidth > 0) {
        // x and y values are normalized here and not accounting for extra paddings. These will be added back in place where the values are
        // scaled back to the host's size.
        clickRegion.setRect(xStart, yStart, regionWidth, regionHeight);
      }
    }

    if (myHoverHandler != null) {
      // Re-pick based on cached mouse data and newly updated model data.
      E pickData = calculatePickData();
      myHoverHandler.accept(pickData);
    }
  }

  @Override
  public void renderLines(@NotNull LineChart lineChart,
                          @NotNull Graphics2D g2d,
                          @NotNull List<Path2D> transformedPaths,
                          @NotNull List<RangedContinuousSeries> series) {
    BiPredicate<SeriesData<E>, RangedContinuousSeries> renderSeriesPredicate = myModel.getRenderSeriesPredicate();
    if (myDurationBgColor != null || !myCustomLineConfigs.isEmpty() || renderSeriesPredicate != null) {
      Shape originalClip = g2d.getClip();
      Dimension dim = lineChart.getSize();

      Rectangle2D clipRect = new Rectangle2D.Float();
      // Build the list of configs for the corresponding series. Use a custom config if it has been specified, otherwise grab the default
      // config from the LineChart.
      List<LineConfig> configs = new ArrayList<>(series.size());
      for (RangedContinuousSeries rangedSeries : series) {
        LineConfig config = lineChart.getLineConfig(rangedSeries);
        if (myCustomLineConfigs.containsKey(rangedSeries)) {
          LineConfig customConfig = myCustomLineConfigs.get(rangedSeries);
          // Dash phases can be modified during the LineChart update loop, so we have to copy any changes over to the custom config.
          if (config.isAdjustDash() && customConfig.isAdjustDash()) {
            customConfig.setAdjustedDashPhase(config.getAdjustedDashPhase());
          }
          config = customConfig;
        }
        configs.add(config);
      }

      for (int i = 0; i < myPathCache.size(); i++) {
        Rectangle2D.Float rect = myPathCache.get(i);
        SeriesData<E> data = myDataCache.get(i);
        double scaledXStart = rect.x * dim.getWidth();
        double scaledXDuration = rect.width * dim.getWidth();
        double newX = Math.max(scaledXStart, originalClip.getBounds().getX());
        clipRect.setRect(newX,
                         originalClip.getBounds().getY(),
                         Math.min(scaledXDuration + scaledXStart - newX,
                                  originalClip.getBounds().getX() + originalClip.getBounds().getWidth() - newX),
                         Math.min(originalClip.getBounds().getHeight(), dim.getHeight()));

        // Paint the background
        g2d.setColor(myDurationBgColor == null ? lineChart.getBackground() : myDurationBgColor);
        g2d.setClip(clipRect);
        g2d.fill(clipRect);
        // Redraw lines in clipRect.
        for (int j = 0; j < transformedPaths.size(); ++j) {
          RangedContinuousSeries rangedSeries = series.get(j);
          // Skip rendering the line if the DurationDataModel decides to not show the series within the current data's duration.
          if (renderSeriesPredicate != null && !renderSeriesPredicate.test(data, rangedSeries)) {
            continue;
          }
          LineChart.drawLine(g2d, transformedPaths.get(j), configs.get(j));
        }
        g2d.setClip(originalClip);
      }
    }

    // Draw the start/end lines if stroke has been set.
    if (myStroke != null) {
      g2d.setColor(myColor);
      g2d.setStroke(myStroke);
      Line2D eventLine = new Line2D.Float();
      for (Rectangle2D.Float rect : myPathCache) {
        double scaledXStart = rect.x * lineChart.getWidth();
        double scaledXDuration = rect.width * lineChart.getWidth();
        g2d.translate(scaledXStart, 0);
        eventLine.setLine(0, 0, 0, lineChart.getHeight());
        g2d.draw(eventLine);
        eventLine.setLine(scaledXDuration, 0, scaledXDuration, lineChart.getHeight());
        g2d.draw(eventLine);
        g2d.translate(-scaledXStart, 0);
      }
    }
  }

  @VisibleForTesting Rectangle2D.Float getScaledClickRegion(@NotNull Rectangle2D.Float rect, int componentWidth, int componentHeight) {
    float paddedHeight = rect.height + myClickRegionPaddingY * 2;
    float paddedWidth = rect.width + myClickRegionPaddingX * 2;
    int totalXInsets = myHostInsets.left + myHostInsets.right;
    float scaledStartX = myHostInsets.left + rect.x * (componentWidth - totalXInsets) + myLabelXOffset + myLineStrokeOffset;
    float scaledStartY = getClampedLabelY(rect.y, paddedHeight, componentHeight);
    return new Rectangle2D.Float(scaledStartX, scaledStartY, paddedWidth, paddedHeight);
  }

  public void renderOverlay(@NotNull Component host, @NotNull Graphics2D g2d) {
    for (int i = 0; i < myClickRegionCache.size(); i++) {
      Rectangle2D.Float rect = getScaledClickRegion(myClickRegionCache.get(i), host.getWidth(), host.getHeight());
      if (myLabelBgColor != null) {
        g2d.setColor(myLabelBgColor);
        if (myMousePosition != null && rect.contains(myMousePosition)) {
          g2d.setColor((myHoverHandler != null || (myClick && myClickHandler != null)) ? myLabelClickedBgColor : myLabelHoveredBgColor);
        }
        g2d.fill(rect);
      }

      rect.x += myClickRegionPaddingX;
      rect.y += myClickRegionPaddingY;
      g2d.translate(rect.x, rect.y);
      Icon icon = getIcon(myDataCache.get(i).value);
      if (icon != null) {
        icon.paintIcon(host, g2d, 0, 0);
        float shift = icon.getIconWidth();
        g2d.translate(shift, 0);
        rect.x += shift;  // keep track of the amount of shift to revert the translate at the end.
      }

      if (myLabelProvider != null) {
        myLabelCache.get(i).paint(g2d);
      }

      g2d.translate(-rect.x, -rect.y);
    }

    myClick = false;
  }

  private boolean isHoveringOverClickRegion(@NotNull Component overlayComponent, @NotNull MouseEvent event) {
    for (int i = 0; i < myClickRegionCache.size(); ++i) {
      Rectangle2D.Float rect = getScaledClickRegion(myClickRegionCache.get(i), overlayComponent.getWidth(), overlayComponent.getHeight());
      if (myMousePosition != null && rect.contains(myMousePosition)) {
        return true;
      }
    }
    return false;
  }

  public boolean handleMouseEvent(@NotNull Component overlayComponent, @NotNull Component selectionComponent, @NotNull MouseEvent event) {
    myMousePosition = event.getPoint();
    if (event.getID() == MouseEvent.MOUSE_ENTERED) {
      myInComponentRegion = true;
    }
    else if (event.getID() == MouseEvent.MOUSE_EXITED) {
      myInComponentRegion = false;
    }

    myMouseComponent = event.getComponent();
    E pickData = calculatePickData();

    if (myHoverHandler != null) {
      myHoverHandler.accept(pickData);
    }

    myClick = event.getClickCount() > 0;
    if (myClickHandler != null && myClick && pickData != null) {
      myClickHandler.accept(pickData);
      return true;
    }

    if (isHoveringOverClickRegion(overlayComponent, event)) {
      selectionComponent.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      return true;
    }

    return false;
  }

  /**
   * "Picking" is an old term for "detecting what is under the cursor during rendering".
   */
  @Nullable
  private E calculatePickData() {
    if (!myInComponentRegion || myMouseComponent == null) {
      return null;
    }

    E closestData = null;
    double closestManhattanDistance = Float.MAX_VALUE;
    Dimension dim = myMouseComponent.getSize();
    for (int i = 0; i < myDataCache.size(); i++) {
      Rectangle2D.Float rect = myClickRegionCache.get(i);
      float paddedWidth = rect.width + myClickRegionPaddingX * 2;
      float paddedHeight = rect.height + myClickRegionPaddingY * 2;
      float scaledY = getClampedLabelY(rect.y, paddedHeight, dim.height);
      Rectangle2D.Float scaledRect = new Rectangle2D.Float(rect.x * dim.width + myLabelXOffset + myLineStrokeOffset,
                                                           scaledY,
                                                           paddedWidth,
                                                           paddedWidth);
      if (scaledRect.contains(myMousePosition)) {
        // Since we're using Manhattan distance, we can rearrange all the terms.
        double manhattanDistance = Math.abs(myMousePosition.getX() + myMousePosition.getY() -
                                            scaledRect.getX() -
                                            scaledRect.getY() -
                                            (scaledRect.getWidth() + scaledRect.getHeight()) * 0.5);
        if (manhattanDistance < closestManhattanDistance) {
          closestManhattanDistance = manhattanDistance;
          closestData = myDataCache.get(i).value;
        }
      }
    }

    return closestData;
  }

  /**
   * Clamp and return the y position (accounting for height + custom offsets) of the label so that it is always within bounds of the host.
   */
  private float getClampedLabelY(float normalizedY, float height, int hostHeight) {
    float totalYInsets = myHostInsets.top + myHostInsets.bottom;
    float maxScaledY = hostHeight - myHostInsets.bottom - height;
    float scaledY = myHostInsets.top + normalizedY * (hostHeight - totalYInsets) - height + myLabelYOffset;
    return Math.max(myHostInsets.top, Math.min(scaledY, maxScaledY));
  }

  /**
   * If an icon mapper is set, return the icon mapped from the given duration data.
   * If an icon is set, return the icon.
   * Otherwise return null.
   */
  @VisibleForTesting
  @Nullable
  Icon getIcon(E durationData) {
    if (myIconMapper != null) {
      return myIconMapper.apply(durationData);
    }
    if (myIcon != null) {
      return myIcon;
    }
    return null;
  }

  public static class Builder<E extends DurationData> {
    // Required
    @NotNull private final DurationDataModel<E> myModel;
    @NotNull private final Color myColor;
    @Nullable private Color myDurationBgColor;
    @Nullable private Icon myIcon = null;
    @Nullable private Function<E, Icon> myIconMapper = null;
    @Nullable private Stroke myStroke = null;
    @Nullable private Function<E, String> myLabelProvider = null;
    @Nullable private Consumer<E> myClickHandler = null;
    @Nullable private Consumer<E> myHoverHandler = null;
    @Nullable private Color myLabelBgColor = null;
    @Nullable private Color myLabelHoveredBgColor = null;
    @Nullable private Color myLabelClickedBgColor = null;
    @Nullable private Color myLabelTextColor = null;
    private float myLabelXOffset;
    private float myLabelYOffset;
    @NotNull private Insets myHostInsets = new Insets(0, 0, 0, 0);
    private int myClickRegionPaddingX = 4;
    private int myClickRegionPaddingY = 2;

    public Builder(@NotNull DurationDataModel<E> model, @NotNull Color color) {
      myModel = model;
      myColor = color;
    }

    public Builder<E> setDurationBg(@NotNull Color durationBgColor) {
      myDurationBgColor = durationBgColor;
      return this;
    }

    /**
     * Sets the icon which will be drawn at the start point of each DurationData.
     */
    public Builder<E> setIcon(@NotNull Icon icon) {
      myIcon = icon;
      return this;
    }

    /**
     * Sets the icon mapper which maps a data point to an icon.
     */
    public Builder<E> setIconMapper(@Nullable Function<E, Icon> iconMapper) {
      myIconMapper = iconMapper;
      return this;
    }

    /**
     * Sets the stroke of the lines that mark the start and end of the DurationData.
     */
    public Builder<E> setStroke(@NotNull Stroke stroke) {
      myStroke = stroke;
      return this;
    }

    /**
     * Sets the provider of the string which will be drawn at the start point of each DurationData.
     */
    public Builder<E> setLabelProvider(@NotNull Function<E, String> provider) {
      myLabelProvider = provider;
      return this;
    }

    /**
     * If set, the handler will get triggered when the user clicked on the icon+label region of the DurationData.
     */
    public Builder<E> setClickHander(@NotNull Consumer<E> handler) {
      myClickHandler = handler;
      return this;
    }

    /**
     * If set, the handler will get triggered when the user hovers on the icon+label region of the DurationData.
     */
    public Builder<E> setHoverHandler(@NotNull Consumer<E> handler) {
      myHoverHandler = handler;
      return this;
    }

    /**
     * Sets the colors to use for the label.
     */
    public Builder<E> setLabelColors(@NotNull Color bgColor,
                                     @NotNull Color hoveredColor,
                                     @NotNull Color clickedColor,
                                     @NotNull Color color) {
      myLabelBgColor = bgColor;
      myLabelHoveredBgColor = hoveredColor;
      myLabelClickedBgColor = clickedColor;
      myLabelTextColor = color;
      return this;
    }

    public Builder<E> setLabelOffsets(float xOffset, float yOffset) {
      myLabelXOffset = xOffset;
      myLabelYOffset = yOffset;
      return this;
    }

    public Builder<E> setHostInsets(@NotNull Insets insets) {
      myHostInsets = insets;
      return this;
    }

    public Builder<E> setClickRegionPadding(int xPadding, int yPadding) {
      myClickRegionPaddingX = xPadding;
      myClickRegionPaddingY = yPadding;
      return this;
    }

    @NotNull
    public DurationDataRenderer<E> build() {
      return new DurationDataRenderer<>(myModel, this);
    }
  }
}
