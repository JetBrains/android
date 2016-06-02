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
import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.RangedDiscreteSeries;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;

public class ThreadsSegment extends BaseSegment implements Animatable {

  private static final String SEGMENT_NAME = "Threads";

  private static final int THREADS_CHART_VERTICAL_PADDING = JBUI.scale(2);

  private static final int LIST_ITEM_SELECTION_BORDER_THICKNESS = JBUI.scale(2);

  private static final int THREADS_NAME_LEFT_MARGIN = JBUI.scale(10);

  private static final Color THREADS_NAME_TEXT_COLOR = new JBColor(Color.BLACK, Color.BLACK);

  private static final LineBorder LIST_ITEM_SELECTED_BORDER = new LineBorder(JBColor.BLUE, LIST_ITEM_SELECTION_BORDER_THICKNESS);

  private static final EmptyBorder LIST_ITEM_UNSELECTED_BORDER = new EmptyBorder(0, 0, 0, 0);

  private final int mFontHeight = JBUI.scale(getFontMetrics(AdtUiUtils.DEFAULT_FONT).getHeight());

  /**
   * Chart height corresponds to font's height + two times the vertical padding (bottom + top).
   */
  private final int mThreadsChartHeight = mFontHeight + THREADS_CHART_VERTICAL_PADDING * 2;

  /**
   * Determine cell height based on the height of the font used in its label.
   */
  private final int mCellHeight = 2 * mFontHeight;

  /**
   * Chart y corresponds cell height subtracting the chart height itself.
   * We divide it by two to take into account that bottom spacing should take as much space as top.
   */
  private final int mThreadsChartY = (mCellHeight - mThreadsChartHeight) / 2;

  private final Map<Thread, StateChart<Thread.State>> mThreadsStateCharts = new HashMap<>();

  private final DefaultListModel<Thread> mThreadsListModel = new DefaultListModel<>();

  private final JBList mThreadsList = new JBList(mThreadsListModel);

  @NotNull
  private final Range mTimeRange;

  private static EnumMap<Thread.State, Color> mThreadStateColor;

  /**
   * Listens to changes in the threads list selection. In case it's null, changes in the selection won't notify anything.
   */
  @Nullable
  private final ThreadSelectedListener mThreadSelectedListener;

  /**
   * Stores the state series corresponding to each thread.
   * TODO: maybe it's safer to keep insertion order
   */
  @NotNull
  private final Map<Thread, RangedDiscreteSeries<Thread.State>> mThreadsStateSeries;

  public ThreadsSegment(@NotNull Range timeRange, @Nullable ThreadSelectedListener threadSelectedListener) {
    super(SEGMENT_NAME, timeRange);
    mTimeRange = timeRange;
    mThreadsStateSeries = new HashMap<>();
    mThreadSelectedListener = threadSelectedListener;
    initialize();
  }

  public ThreadsSegment(@NotNull Range timeRange) {
    this(timeRange, null);
  }

  @NotNull
  private static EnumMap<Thread.State, Color> getThreadStateColor() {
    if (mThreadStateColor != null) {
      return mThreadStateColor;
    }
    // TODO: change it to use the proper darcula colors
    mThreadStateColor = new EnumMap<>(Thread.State.class);
    mThreadStateColor.put(Thread.State.RUNNABLE, new JBColor(0x6cc17b, 0x6cc17b));
    mThreadStateColor.put(Thread.State.TIMED_WAITING, new JBColor(0xaaaaaa, 0xaaaaaa));
    mThreadStateColor.put(Thread.State.WAITING, new JBColor(0xaaaaaa, 0xaaaaaa));
    mThreadStateColor.put(Thread.State.BLOCKED, new JBColor(0x5a5a5a, 0x5a5a5a));
    mThreadStateColor.put(Thread.State.TERMINATED, AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
    return mThreadStateColor;
  }

  private void initialize() {
    mThreadsList.setCellRenderer(new ThreadsStateCellRenderer());
    mThreadsList.setSelectionBackground(AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
    mThreadsList.setFixedCellHeight(mCellHeight);
    if (mThreadSelectedListener != null) {
      mThreadsList.addListSelectionListener((event) -> {
        int[] selectedThreadsIndices = mThreadsList.getSelectedIndices();
        List<Thread> selectedThreads = new ArrayList<>();
        for (int i = 0; i < selectedThreadsIndices.length; i++) {
          selectedThreads.add(mThreadsListModel.get(i));
        }
        mThreadSelectedListener.onSelected(selectedThreads);
      });
    }
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

  @Override
  protected void setLeftContent(@NotNull JPanel panel) {
    setLeftSpacerVisible(false);
  }

  public void addThreadStateSeries(Thread thread) {
    if (mThreadsStateSeries.containsKey(thread)) {
      return; // Early return in case the thread is already represented by a series.
    }
    createThreadStateChart(thread);
  }

  public Map<Thread, RangedDiscreteSeries<Thread.State>> getThreadsStateSeries() {
    return mThreadsStateSeries;
  }

  private void createThreadStateChart(Thread thread) {
    StateChart<Thread.State> stateChart = new StateChart<>(getThreadStateColor());
    mThreadsStateCharts.put(thread, stateChart);

    RangedDiscreteSeries<Thread.State> threadSeries = new RangedDiscreteSeries<>(Thread.State.class, mTimeRange);
    mThreadsStateSeries.put(thread, threadSeries);
    mThreadsStateCharts.get(thread).addSeries(threadSeries);

    mThreadsListModel.addElement(thread);
  }

  @Override
  public void reset() {
    mThreadsStateCharts.values().forEach(AnimatedComponent::reset);
  }

  @Override
  public void animate(float frameLength) {
    mThreadsStateCharts.values().forEach(chart -> chart.animate(frameLength));
  }

  @Override
  public void postAnimate() {
    mThreadsStateCharts.values().forEach(AnimatedComponent::postAnimate);
  }

  public interface ThreadSelectedListener {
    void onSelected(@NotNull List<Thread> selectedThreads);
  }

  private class ThreadsStateCellRenderer implements ListCellRenderer<Thread> {
    @Override
    public Component getListCellRendererComponent(JList<? extends Thread> list,
                                                  Thread thread,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {

      JLayeredPane cellPane = new JLayeredPane();
      cellPane.setBounds(0, 0, list.getWidth(), mCellHeight);

      Border cellBorder;
      if (isSelected) {
        cellBorder = LIST_ITEM_SELECTED_BORDER;
      } else {
        cellBorder = LIST_ITEM_UNSELECTED_BORDER;
      }
      cellPane.setBorder(cellBorder);

      // Cell label (thread name)
      JLabel threadName = new JLabel(thread.getName());
      threadName.setFont(AdtUiUtils.DEFAULT_FONT);
      // TODO: Fix color when setting proper darcula colors to state chart.
      threadName.setForeground(THREADS_NAME_TEXT_COLOR);
      threadName.setBounds(THREADS_NAME_LEFT_MARGIN, 0, list.getWidth(), mCellHeight);

      // Cell content (state chart containing the thread states)
      StateChart<Thread.State> threadStateChart = mThreadsStateCharts.get(thread);
      // State chart should be aligned with CPU Usage line charts and, therefore, with center content
      threadStateChart.setBounds(getSpacerWidth(), mThreadsChartY, list.getWidth() - getSpacerWidth(), mThreadsChartHeight);

      cellPane.add(threadName);
      cellPane.add(threadStateChart);

      return cellPane;
    }
  }
}
