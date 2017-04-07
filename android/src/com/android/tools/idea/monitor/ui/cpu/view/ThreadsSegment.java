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
package com.android.tools.idea.monitor.ui.cpu.view;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.LegendRenderData;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.DataStoreSeries;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.cpu.model.ThreadAddedNotifier;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.cpu.ThreadStateDataSeries;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class ThreadsSegment extends BaseSegment implements Animatable {

  private static final String SEGMENT_NAME = "Threads";

  private static final String RUNNING_LABEL = "Runnable";

  private static final String SLEEPING_LABEL = "Waiting";

  private static final String BLOCKED_LABEL = "Blocked";

  private static final int THREADS_NAME_LEFT_MARGIN = JBUI.scale(10);

  private static final Color LIST_ITEM_HOVER_BACKGROUND = new JBColor(new Color(22, 80, 197, 23), new Color(255, 255, 255, 13));

  private static final Color LIST_ITEM_SELECTED_BACKGROUND = new JBColor(new Color(70, 118, 187), new Color(70, 118, 187));

  private final int mFontHeight = JBUI.scale(getFontMetrics(AdtUiUtils.DEFAULT_FONT).getHeight());

  /**
   * Determine cell height based on the height of the font used in its label.
   */
  private final int mCellHeight = 2 * mFontHeight;

  /**
   * Chart y corresponds cell height subtracting the chart height itself, which in turn is determined by the font height.
   * We divide it by two to take into account that bottom spacing should take as much space as top.
   */
  private final int mThreadsChartY = (mCellHeight - mFontHeight) / 2;

  private final Map<ThreadStateDataSeries, StateChart<CpuProfiler.ThreadActivity.State>> mThreadsStateCharts = new HashMap<>();


  private final DefaultListModel<ThreadStateDataSeries> mThreadsListModel = new DefaultListModel<>();

  private final JBList mThreadsList = new JBList(mThreadsListModel);

  /**
   * Keep the index of the thread item list currently hovered (or -1 if the mouse is not over any item).
   */
  private int mHoveredListIndex = -1;

  private static EnumMap<CpuProfiler.ThreadActivity.State, Color> mThreadStateColor;

  /**
   * Listens to changes in the threads list selection. In case it's null, changes in the selection won't notify anything.
   */
  @Nullable
  private final ThreadSelectedListener mThreadSelectedListener;

  @NotNull
  private SeriesDataStore mSeriesDataStore;

  @NotNull
  private final ThreadAddedNotifier myThreadAddedNotifier;

  private LegendComponent mLegendComponent;

  public ThreadsSegment(@NotNull Range timeCurrentRangeUs,
                        @NotNull SeriesDataStore dataStore,
                        @NotNull EventDispatcher<ProfilerEventListener> dispatcher,
                        @Nullable ThreadSelectedListener threadSelectedListener) {
    super(SEGMENT_NAME, timeCurrentRangeUs, dispatcher);
    mSeriesDataStore = dataStore;
    mThreadSelectedListener = threadSelectedListener;
    myThreadAddedNotifier = (threadStatesDataModel) -> {
      if (mThreadsStateCharts.containsKey(threadStatesDataModel)) {
        return; // Early return in case the chart correspondent to this model already exists
      }
      createThreadStateChart(threadStatesDataModel);
    };
    initialize();
  }

  @Override
  protected boolean hasLeftContent() {
    return false;
  }

  @Override
  protected boolean hasRightContent() {
    return false;
  }

  @NotNull
  private static EnumMap<CpuProfiler.ThreadActivity.State, Color> getThreadStateColor() {
    if (mThreadStateColor != null) {
      return mThreadStateColor;
    }
    // TODO: change it to use the proper darcula colors. Also, support other states if needed.
    mThreadStateColor = new EnumMap<>(CpuProfiler.ThreadActivity.State.class);
    mThreadStateColor.put(CpuProfiler.ThreadActivity.State.RUNNING, new JBColor(new Color(134, 199, 144), new Color(134, 199, 144)));
    mThreadStateColor.put(CpuProfiler.ThreadActivity.State.SLEEPING, new JBColor(Gray._189, Gray._189));
    mThreadStateColor.put(CpuProfiler.ThreadActivity.State.DEAD, Gray.TRANSPARENT);
    return mThreadStateColor;
  }

  private void initialize() {
    initializeStateChartsList();
    initializeLegendComponent();
  }

  private void initializeLegendComponent() {
    List<LegendRenderData> legendRenderDataList = new ArrayList<>();
    // Running state
    legendRenderDataList.add(new LegendRenderData(
      LegendRenderData.IconType.BOX, getThreadStateColor().get(CpuProfiler.ThreadActivity.State.RUNNING), RUNNING_LABEL));
    // Sleeping state
    legendRenderDataList.add(new LegendRenderData(
      LegendRenderData.IconType.BOX, getThreadStateColor().get(CpuProfiler.ThreadActivity.State.SLEEPING), SLEEPING_LABEL));
    // Blocked state. TODO: support this state later if we actually should do so
    legendRenderDataList.add(new LegendRenderData(
      LegendRenderData.IconType.BOX, new JBColor(new Color(199, 65, 101), new Color(199, 65, 101)), BLOCKED_LABEL));

    mLegendComponent = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, Integer.MAX_VALUE /* No need to update */);
    mLegendComponent.setLegendData(legendRenderDataList);
  }

  private void initializeStateChartsList() {
    mThreadsList.setCellRenderer(new ThreadsStateCellRenderer());
    mThreadsList.setSelectionBackground(AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
    mThreadsList.setFixedCellHeight(mCellHeight);
    if (mThreadSelectedListener != null) {
      mThreadsList.addListSelectionListener((event) -> {
        int[] selectedThreadsIndices = mThreadsList.getSelectedIndices();
        List<ThreadStateDataSeries> selectedThreads = new ArrayList<>();
        for (int i = 0; i < selectedThreadsIndices.length; i++) {
          selectedThreads.add(mThreadsListModel.get(selectedThreadsIndices[i]));
        }
        mThreadSelectedListener.onSelected(selectedThreads);
      });
    }
    mThreadsList.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent me) {
        Point p = new Point(me.getX(),me.getY());
        int index = mThreadsList.locationToIndex(p);
        if (index != mHoveredListIndex) {
          mHoveredListIndex = index;
          mThreadsList.repaint();
        }
      }
    });
  }

  @Override
  protected void setTopCenterContent(@NotNull JPanel panel) {
    panel.add(mLegendComponent, BorderLayout.EAST);
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    JLayeredPane centerPane = new JBLayeredPane();
    JScrollPane scrollPane = new JBScrollPane(mThreadsList);
    centerPane.add(scrollPane);
    centerPane.addComponentListener(new ComponentAdapter() {
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
    panel.add(centerPane, BorderLayout.CENTER);
  }

  @NotNull
  public ThreadAddedNotifier getThreadAddedNotifier() {
    return myThreadAddedNotifier;
  }

  private void createThreadStateChart(ThreadStateDataSeries threadStateDataSeries) {
    StateChart<CpuProfiler.ThreadActivity.State> stateChart = new StateChart<>(getThreadStateColor());
    mThreadsStateCharts.put(threadStateDataSeries, stateChart);

    DataStoreSeries<CpuProfiler.ThreadActivity.State> defaultData =
      new DataStoreSeries<>(mSeriesDataStore, SeriesDataType.CPU_THREAD_STATE, threadStateDataSeries);
    RangedSeries<CpuProfiler.ThreadActivity.State> threadSeries = new RangedSeries<>(myTimeCurrentRangeUs, defaultData);
    mThreadsStateCharts.get(threadStateDataSeries).addSeries(threadSeries);
    mThreadsListModel.addElement(threadStateDataSeries);
  }

  @Override
  public void reset() {
    mThreadsStateCharts.values().forEach(AnimatedComponent::reset);
    mLegendComponent.reset();
  }

  @Override
  public void animate(float frameLength) {
    mThreadsStateCharts.values().forEach(chart -> chart.animate(frameLength));
    mLegendComponent.animate(frameLength);
  }

  @Override
  public void postAnimate() {
    mThreadsStateCharts.values().forEach(AnimatedComponent::postAnimate);
    mLegendComponent.postAnimate();
  }

  public interface ThreadSelectedListener {
    void onSelected(@NotNull List<ThreadStateDataSeries> selectedThreads);
  }

  private class ThreadsStateCellRenderer implements ListCellRenderer<ThreadStateDataSeries> {
    @Override
    public Component getListCellRendererComponent(JList<? extends ThreadStateDataSeries> list,
                                                  ThreadStateDataSeries threadStateDataSeries,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {

      JLayeredPane cellPane = new JLayeredPane();
      cellPane.setOpaque(true);
      cellPane.setBounds(0, 0, list.getWidth(), mCellHeight);

      Color cellBackground = AdtUiUtils.DEFAULT_BACKGROUND_COLOR;

      if (isSelected) {
        cellBackground = LIST_ITEM_SELECTED_BACKGROUND;
      } else if (mHoveredListIndex == index) {
        cellBackground = LIST_ITEM_HOVER_BACKGROUND;
      }
      cellPane.setBackground(cellBackground);

      // Cell label (thread name)
   //   JLabel threadName = new JLabel(threadStateDataSeries.getName());
   //   threadName.setFont(AdtUiUtils.DEFAULT_FONT);
   //   threadName.setBounds(THREADS_NAME_LEFT_MARGIN, 0, list.getWidth(), mCellHeight);
   //
   //   // Cell content (state chart containing the thread states)
   //   StateChart<CpuProfiler.ThreadActivity.State> threadStateChart = mThreadsStateCharts.get(threadStateDataSeries);
   //   // State chart should be aligned with CPU Usage line charts and, therefore, with center content
   //   threadStateChart.setBounds(getSpacerWidth(), mThreadsChartY, list.getWidth() - getSpacerWidth(), mFontHeight);
   //
   //   cellPane.add(threadName);
   //   cellPane.add(threadStateChart);

      return cellPane;
    }
  }
}
