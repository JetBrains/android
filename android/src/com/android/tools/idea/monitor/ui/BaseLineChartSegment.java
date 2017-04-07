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
package com.android.tools.idea.monitor.ui;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.*;
import com.android.tools.datastore.DataStoreSeries;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * This class can be used for line charts with a left axis or with both left and right.
 * In former case rightAxisFormatter should be passed as null value during construction, indicating that the chart has a left axis only. In
 * doing so, attempting to add a right axis line afterwards will result in an IllegalComponentStateException.
 * When rightAxisFormatter is provided, the right axis will not be shown until at least one right axis line is added to this chart segment.
 */
public abstract class BaseLineChartSegment extends BaseSegment {

  /**
   * TODO consider getting OS/system specific double-click intervals.
   * If this is too large, however, the delay in dispatching the queued events would be significantly noticeable. The lag is undesirable
   * if the user is trying to perform other operations such as selection.
   */
  private static final int MULTI_CLICK_INTERVAL_MS = 300;

  /**
   * A mouse drag threshold (in pixel) to short circuit the double-click detection logic. Once the user starts dragging the mouse beyond
   * this distance value, all queued up events will be dispatched immediately.
   */
  private static final int MOUSE_DRAG_DISTANCE_THRESHOLD_PX = 5;

  private static final int MULTI_CLICK_THRESHOLD = 2;

  @NotNull
  protected AnimatedRange mLeftAxisRange;

  @NotNull
  protected AnimatedRange mRightAxisRange;

  private AxisComponent mLeftAxis;

  private AxisComponent mRightAxis;

  @NotNull
  private final BaseAxisFormatter mLeftAxisFormatterSimple;

  @NotNull
  private final BaseAxisFormatter mLeftAxisFormatter;

  @Nullable
  private final BaseAxisFormatter mRightAxisFormatter;

  private GridComponent mGrid;

  private LineChart mLineChart;

  protected LegendComponent mLegendComponent;

  @NotNull
  protected SeriesDataStore mSeriesDataStore;

  /**
   * Mouse events that are queued up as the segment waits for the double-click event. See {@link #initializeListeners()}.
   */
  private final ArrayDeque<MouseEvent> mDelayedEvents;

  private boolean mMultiClicked;

  private Point mMousePressedPosition;

  /**
   * @param rightAxisFormatter if it is null, chart will have a left axis only
   * @param leftAxisRange if it is null, a default range is going to be used
   * @param rightAxisRange if it is null, a default range is going to be used
   */
  public BaseLineChartSegment(@NotNull String name,
                              @NotNull Range timeCurrentRangeUs,
                              @NotNull SeriesDataStore dataStore,
                              @NotNull BaseAxisFormatter leftAxisFormatterSimple,
                              @NotNull BaseAxisFormatter leftAxisFormatter,
                              @Nullable BaseAxisFormatter rightAxisFormatter,
                              @Nullable AnimatedRange leftAxisRange,
                              @Nullable AnimatedRange rightAxisRange,
                              @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(name, timeCurrentRangeUs, dispatcher);
    mLeftAxisFormatterSimple = leftAxisFormatterSimple;
    mLeftAxisFormatter = leftAxisFormatter;
    mRightAxisFormatter = rightAxisFormatter;
    mLeftAxisRange = leftAxisRange != null ? leftAxisRange : new AnimatedRange();
    mRightAxisRange = rightAxisRange != null ? rightAxisRange : new AnimatedRange();
    mSeriesDataStore = dataStore;
    mDelayedEvents = new ArrayDeque<>();

    initializeListeners();
  }

  public BaseLineChartSegment(@NotNull String name,
                              @NotNull Range timeCurrentRangeUs,
                              @NotNull SeriesDataStore dataStore,
                              @NotNull BaseAxisFormatter leftAxisFormatterSimple,
                              @NotNull BaseAxisFormatter leftAxisFormatter,
                              @Nullable BaseAxisFormatter rightAxisFormatter,
                              @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    this(name, timeCurrentRangeUs, dataStore, leftAxisFormatterSimple, leftAxisFormatter, rightAxisFormatter, null, null, dispatcher);
  }

  public abstract BaseProfilerUiManager.ProfilerType getProfilerType();

  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {
    // left axis
    AxisComponent.Builder builder = new AxisComponent.Builder(mLeftAxisRange, mLeftAxisFormatterSimple, AxisComponent.AxisOrientation.LEFT)
      .showMax(true)
      .clampToMajorTicks(true);
    mLeftAxis = builder.build();

    // right axis
    if (mRightAxisFormatter != null) {
      builder = new AxisComponent.Builder(mRightAxisRange, mRightAxisFormatter, AxisComponent.AxisOrientation.RIGHT)
        .showMax(true)
        .setParentAxis(mLeftAxis);
      mRightAxis = builder.build();
      mRightAxis.setParentAxis(mLeftAxis);
      mRightAxis.setVisible(false);
    }

    mLineChart = new LineChart();
    mGrid = new GridComponent();
    mGrid.addAxis(mLeftAxis);
    mGrid.setVisible(false);

    mLegendComponent = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, 100);

    // Note: the order below is important as some components depend on
    // others to be updated first. e.g. the ranges need to be updated before the axes.
    // The comment on each line highlights why the component needs to be in that position.
    animatables.add(mLineChart); // Set y's interpolation values.
    animatables.add(mLeftAxis);  // Read left y range and update its max to the next major tick.
    if (mRightAxis != null) {
      animatables.add(mRightAxis); // Read right y range and update its max by syncing to the left axis' major tick spacing.
    }
    animatables.add(mLeftAxisRange); // Interpolate left y range.
    animatables.add(mRightAxisRange); // Interpolate right y range.
    animatables.add(mLegendComponent);
    animatables.add(mGrid); // No-op.
  }

  @Override
  protected void setLeftContent(@NotNull JPanel panel) {
    panel.add(mLeftAxis, BorderLayout.CENTER);
  }

  @Override
  protected void setTopCenterContent(@NotNull JPanel panel) {
    panel.add(mLegendComponent, BorderLayout.EAST);
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    JBLayeredPane layeredPane = new JBLayeredPane();
    layeredPane.add(mLineChart);
    layeredPane.add(mGrid);
    layeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          for (Component c : host.getComponents()) {
            c.setBounds(0, 0, dim.width, dim.height);
          }
        }
      }
    });
    panel.add(layeredPane, BorderLayout.CENTER);
  }

  @Override
  protected void setRightContent(@NotNull JPanel panel) {
    if (mRightAxisFormatter != null) {
      panel.add(mRightAxis, BorderLayout.CENTER);
      setRightSpacerVisible(true);
    }
  }

  private void initializeListeners() {
    // Add mouse listener to support expand/collapse when user double-clicks on the Segment.
    // Note that other mouse events have to be queued up for a certain delay to allow the listener to detect the second click.
    // If the second click event has not arrived within the time limit, the queued events are dispatched up the tree to allow other
    // components to perform operations such as selection.
    addMouseListener(new MouseListener() {
      @Override
      public void mousePressed(MouseEvent e) {
        // Cache the mouse pressed position to detect dragging threshold.
        mMousePressedPosition = e.getPoint();
        if (e.getClickCount() >= MULTI_CLICK_THRESHOLD && !mDelayedEvents.isEmpty()) {
          // If a multi-click event has arrived and the dispatch timer below has not run to dispatch the queue events,
          // then process the multi-click.
          mMultiClicked = true;
          handleMultiClickEvent();
        } else {
          mMultiClicked = false;
          mDelayedEvents.add(e);

          Timer dispatchTimer = new Timer(MULTI_CLICK_INTERVAL_MS, e1 -> dispatchOrAbsorbEvents());
          dispatchTimer.setRepeats(false);
          dispatchTimer.start();
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }
    });

    // MouseMotionListener to detect the distance the user has dragged since the last mouse press.
    // Dispatch the queued events immediately if the threshold is passed.
    addMouseMotionListener(new MouseMotionListener() {
      @Override
      public void mouseDragged(MouseEvent e) {
        dispatchOrDelayEvent(e);
        if (!mDelayedEvents.isEmpty()) {
          double distance = Point.distance(mMousePressedPosition.getX(), mMousePressedPosition.getY(),
                                           e.getPoint().getX(), e.getPoint().getY());
          if (distance > MOUSE_DRAG_DISTANCE_THRESHOLD_PX) {
            dispatchOrAbsorbEvents();
          }
        }
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }
    });
  }

  /**
   * Handle multiclick event on the segment. By default, trigger a profiler expanded event.
   */
  protected void handleMultiClickEvent() {
    mEventDispatcher.getMulticaster().profilerExpanded(getProfilerType());
  }

  /**
   * Queue the MouseEvent if the dispatch timer has started and there are already events in the queue.
   * Dispatch the event immediately to the parent otherwise.
   */
  private void dispatchOrDelayEvent(MouseEvent e) {
    if (mDelayedEvents.isEmpty()) {
      getParent().dispatchEvent(e);
    } else {
      mDelayedEvents.addLast(e);
    }
  }

  /**
   * If a multi-click event has not occurred, dispatch all the queued events to the parent in order.
   * Swallows all the queued events otherwise.
   */
  private void dispatchOrAbsorbEvents() {
    if (mMultiClicked) {
      mDelayedEvents.clear();
    } else {
      while (!mDelayedEvents.isEmpty()) {
        getParent().dispatchEvent(mDelayedEvents.remove());
      }
    }
  }

  /**
   * Toggle between levels 1 and 2.
   * @param isExpanded true if toggling to level 2.
   */
  @Override
  public void toggleView(boolean isExpanded) {
    super.toggleView(isExpanded);
    mGrid.setVisible(isExpanded);
    mLeftAxis.setAxisFormatter(isExpanded ? mLeftAxisFormatter : mLeftAxisFormatterSimple);
    mLineChart.clearConfigs();
    updateChartLines(isExpanded);
    mLegendComponent.setLegendData(getLegendRenderDataList());
  }

  /**
   * Updates the line chart based on the expanded state of the segment. Expanded segments usually display more information/lines.
   */
  protected abstract void updateChartLines(boolean isExpanded);

  /**
   * Returns a list of {@link LegendRenderData} based on the data series currently being rendered in the LineChart.
   */
  private List<LegendRenderData> getLegendRenderDataList() {
    List<LegendRenderData> legendRenderDataList = new ArrayList<>();
    for (RangedContinuousSeries series : mLineChart.getRangedContinuousSeries()) {
      LineConfig lineConfig = mLineChart.getLineConfig(series);
      LegendRenderData.IconType iconType = lineConfig.getLegendIconType();
      // Use a default icon type for the line in case there is no icon set in line config.
      // TODO: use LegendRenderData.IconType.DOTTED_LINE for dashed lines
      if (iconType == null) {
        iconType = lineConfig.isFilled() ? LegendRenderData.IconType.BOX : LegendRenderData.IconType.LINE;
      }
      legendRenderDataList.add(new LegendRenderData(iconType, lineConfig.getColor(), series.getLabel()));
    }
    return legendRenderDataList;
  }

  /**
   * Adds a line to {@link #mLineChart} that is associated with the left axis.
   */
  protected void addLeftAxisLine(SeriesDataType type, String label, LineConfig lineConfig) {
    addLeftAxisLine(type, label, lineConfig, new DataStoreSeries<>(mSeriesDataStore, type));
  }

  protected void addLeftAxisLine(SeriesDataType type, String label, LineConfig lineConfig, DataSeries series) {
    mLineChart.addLine(new RangedContinuousSeries(label, myTimeCurrentRangeUs, mLeftAxisRange, series), lineConfig);
  }

  /**
   * Adds a line to {@link #mLineChart} that is associated with the right axis.
   */
  protected void addRightAxisLine(SeriesDataType type, String label, LineConfig lineConfig) {
    addRightAxisLine(type, label, lineConfig, new DataStoreSeries<>(mSeriesDataStore, type));
  }
  protected void addRightAxisLine(SeriesDataType type, String label, LineConfig lineConfig, DataSeries series) {
    if (mRightAxisFormatter == null) {
      throw new IllegalComponentStateException("Right axis formatter is not defined, cannot add right axis line.");
    }

    mLineChart.addLine(new RangedContinuousSeries(label, myTimeCurrentRangeUs, mRightAxisRange, series), lineConfig);
    mRightAxis.setVisible(true);
  }

  ///**
  // * Adds an event series to {@link #mLineChart}.
  // */
  //protected void addEvent(SeriesDataType type, @NotNull DurationDataConfig eventConfig) {
  //  addEvent(type, eventConfig, new DataStoreSeries<>(mSeriesDataStore, type));
  //}
  //
  //protected void addEvent(SeriesDataType type, @NotNull DurationDataConfig eventConfig, DataSeries series) {
  //  mLineChart.addDurationRenderer(new SimpleDurationOverlayRenderer(new RangedSeries<>(myTimeCurrentRangeUs, series), eventConfig));
  //}
}
