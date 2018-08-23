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
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profilers.DragAndDropList;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.cpu.capturedetails.CaptureModel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.event.*;

/**
 * Creates a view containing a {@link HideablePanel} composed by a {@link CpuListScrollPane} displaying a list of threads and their
 * corresponding {@link com.android.tools.adtui.chart.statechart.StateChart} whose data are the thread state changes.
 */
final class CpuThreadsView {

  @NotNull
  private final HideablePanel myPanel;

  @NotNull
  private final CpuProfilerStage myStage;

  @NotNull
  private final DragAndDropList<CpuThreadsModel.RangedCpuThread> myThreads;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal")
  @NotNull
  private final AspectObserver myObserver;

  public CpuThreadsView(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    myThreads = new DragAndDropList<>(stage.getThreadStates());
    myPanel = createHideablePanel();
    setupListeners();
    myThreads.setBorder(null);
    myThreads.setCellRenderer(new ThreadCellRenderer(myThreads, myStage.getUpdatableManager()));
    myThreads.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // TODO(b/62447834): Make a decision on how we want to handle thread selection.
    myThreads.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    MouseEventHandler mouseHandler = new MouseEventHandler() {
      @Override
      protected void handle(MouseEvent event) {
        // |myPanel| does not receive any mouse events, because all mouse events are consumed by |myThreads|.
        // We're dispatching them manually, so that |CpuProfilerStageView| could register CPU mouse events
        // directly into the top-level component (i.e to |myPanel|) instead of its child.
        myPanel.dispatchEvent(SwingUtilities.convertMouseEvent(myThreads, event, myPanel));
      }
    };
    myThreads.addMouseListener(mouseHandler);
    myThreads.addMouseMotionListener(mouseHandler);

    myPanel.addStateChangedListener((actionEvent) ->
      myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackToggleCpuThreadsHideablePanel()
    );
    myObserver = new AspectObserver();
    stage.getAspect().addDependency(myObserver)
         .onChange(CpuProfilerAspect.SELECTED_THREADS, this::updateThreadSelection);
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  private HideablePanel createHideablePanel() {
    // Add AxisComponent only to scrollable section of threads list.
    final AxisComponent timeAxisGuide = new AxisComponent(myStage.getTimeAxisGuide(), AxisComponent.AxisOrientation.BOTTOM);
    timeAxisGuide.setShowAxisLine(false);
    timeAxisGuide.setShowLabels(false);
    timeAxisGuide.setHideTickAtMin(true);
    timeAxisGuide.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
    final JPanel threads = new JPanel(new TabularLayout("*", "*"));

    final HideablePanel threadsPanel = new HideablePanel.Builder("THREADS", threads)
      .setShowSeparator(false)
      .setClickableComponent(HideablePanel.ClickableComponent.TITLE)
      .build();

    CpuListScrollPane scrollingThreads = new CpuListScrollPane(myThreads, threadsPanel);
    scrollingThreads.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        timeAxisGuide.setMarkerLengths(scrollingThreads.getHeight(), 0);
      }
    });

    threads.add(timeAxisGuide, new TabularLayout.Constraint(0, 0));
    threads.add(scrollingThreads, new TabularLayout.Constraint(0, 0));

    // Clear border set by default on the hideable panel.
    threadsPanel.setBorder(JBUI.Borders.customLine(ProfilerColors.CPU_AXIS_GUIDE_COLOR, 2, 0, 0, 0));
    threadsPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    myThreads.getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        threadsPanel.setTitle(String.format("THREADS (%d)", myThreads.getModel().getSize()));
      }
    });
    threads.setBorder(JBUI.Borders.empty());
    return threadsPanel;
  }

  private void setupListeners() {
    CpuThreadsModel model = myStage.getThreadStates();

    myThreads.addListSelectionListener((e) -> {
      int selectedIndex = myThreads.getSelectedIndex();
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

    myThreads.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myThreads.getSelectedIndex() < 0 && myThreads.getModel().getSize() > 0) {
          myThreads.setSelectedIndex(0);
        }
      }
    });

    myThreads.addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuThreadsTooltip(myStage)));
    myThreads.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = myThreads.locationToIndex(e.getPoint());
        if (row != -1) {
          CpuThreadsModel.RangedCpuThread model = myThreads.getModel().getElementAt(row);
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
  private void updateThreadSelection() {
    if (myStage.getSelectedThread() == CaptureModel.NO_THREAD) {
      myThreads.clearSelection();
      return;
    }

    // Select the thread which has its tree displayed in capture panel in the threads list
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = myThreads.getModel().getElementAt(i);
      if (myStage.getSelectedThread() == thread.getThreadId()) {
        myThreads.setSelectedIndex(i);
        break;
      }
    }
  }
}
