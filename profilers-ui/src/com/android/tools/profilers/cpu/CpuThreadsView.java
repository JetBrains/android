/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profilers.DragAndDropList;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.event.*;

/**
 * Creates a view containing a {@link HideablePanel} composed by a {@link CpuListScrollPane} displaying a list of threads and their
 * corresponding {@link com.android.tools.adtui.chart.statechart.StateChart} whose data are the thread state changes.
 */
public class CpuThreadsView extends DragAndDropList<CpuThreadsModel.RangedCpuThread> {

  @NotNull
  private final HideablePanel myPanel;

  @NotNull
  private final CpuProfilerStage myStage;

  // TODO(b/110524334): Do not expose the parent only to capture mouse events.
  public CpuThreadsView(@NotNull CpuProfilerStage stage, @NotNull JPanel parent) {
    super(stage.getThreadStates());
    myStage = stage;
    myPanel = createHideablePanel(parent);
    setupListeners();
    setBorder(null);
    setCellRenderer(new ThreadCellRenderer(this, myStage.getUpdatableManager()));
    setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // TODO(b/62447834): Make a decision on how we want to handle thread selection.
    getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  @NotNull
  public HideablePanel getPanel() {
    return myPanel;
  }

  private HideablePanel createHideablePanel(@NotNull JPanel parent) {
    // Add AxisComponent only to scrollable section of threads list.
    final AxisComponent timeAxisGuide = new AxisComponent(myStage.getTimeAxisGuide(), AxisComponent.AxisOrientation.BOTTOM);
    timeAxisGuide.setShowAxisLine(false);
    timeAxisGuide.setShowLabels(false);
    timeAxisGuide.setHideTickAtMin(true);
    timeAxisGuide.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
    CpuListScrollPane scrollingThreads = new CpuListScrollPane(this, parent);
    scrollingThreads.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        timeAxisGuide.setMarkerLengths(scrollingThreads.getHeight(), 0);
      }
    });

    final JPanel threads = new JPanel(new TabularLayout("*", "*"));
    threads.add(timeAxisGuide, new TabularLayout.Constraint(0, 0));
    threads.add(scrollingThreads, new TabularLayout.Constraint(0, 0));

    final HideablePanel threadsPanel = new HideablePanel.Builder("THREADS", threads)
      .setShowSeparator(false)
      .setClickableComponent(HideablePanel.ClickableComponent.TITLE)
      .build();
    // Clear border set by default on the hideable panel.
    threadsPanel.setBorder(JBUI.Borders.customLine(ProfilerColors.CPU_AXIS_GUIDE_COLOR, 2, 0, 0, 0));
    threadsPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {

      }

      @Override
      public void intervalRemoved(ListDataEvent e) {

      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        threadsPanel.setTitle(String.format("THREADS (%d)", getModel().getSize()));
      }
    });
    threads.setBorder(JBUI.Borders.empty());
    return threadsPanel;
  }

  private void setupListeners() {
    CpuThreadsModel model = myStage.getThreadStates();

    addListSelectionListener((e) -> {
      int selectedIndex = getSelectedIndex();
      if (selectedIndex >= 0) {
        CpuThreadsModel.RangedCpuThread thread = model.getElementAt(selectedIndex);
        if (myStage.getSelectedThread() != thread.getThreadId()) {
          myStage.setSelectedThread(thread.getThreadId());
          myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectThread();
        }
      }
      else {
        myStage.setSelectedThread(CaptureModel.NO_THREAD);
      }
    });

    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (getSelectedIndex() < 0 && getModel().getSize() > 0) {
          setSelectedIndex(0);
        }
      }
    });

    addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuThreadsTooltip(myStage)));
    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = locationToIndex(e.getPoint());
        if (row != -1) {
          CpuThreadsModel.RangedCpuThread model = getModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuThreadsTooltip) {
            CpuThreadsTooltip tooltip = (CpuThreadsTooltip)myStage.getTooltip();
            tooltip.setThread(model.getName(), model.getStateSeries());
          }
        }
      }
    });
  }

  /**
   * Selects a thread in the list whose ID matches the one set in the model.
   */
  void updateThreadSelection() {
    if (myStage.getSelectedThread() == CaptureModel.NO_THREAD) {
      clearSelection();
      return;
    }

    // Select the thread which has its tree displayed in capture panel in the threads list
    for (int i = 0; i < getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = getModel().getElementAt(i);
      if (myStage.getSelectedThread() == thread.getThreadId()) {
        setSelectedIndex(i);
        break;
      }
    }
  }
}
