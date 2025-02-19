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
import com.android.tools.adtui.DragAndDropList;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.event.DelegateMouseEventHandler;
import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.updater.UpdatableManager;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.util.ui.JBUI;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Creates a view containing a {@link HideablePanel} composed by a {@link CpuListScrollPane} displaying a list of threads and their
 * corresponding {@link com.android.tools.adtui.chart.statechart.StateChart} whose data are the thread state changes.
 */
public final class CpuThreadsView {
  @NotNull
  private final HideablePanel myPanel;
  @NotNull
  private final DragAndDropList<CpuThreadsModel.RangedCpuThread> myThreads;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal")
  @NotNull
  private final AspectObserver myObserver;
  private int myLastHoveredRow = -1;
  @NotNull private final CpuThreadsModel myThreadStates;
  @NotNull private final UpdatableManager myUpdatableManager;
  @NotNull private final StudioProfilers myStudioProfilers;
  @NotNull private final AxisComponentModel myTimeAxisGuide;
  @NotNull private final Supplier<Integer> myGetSelectedThread;
  @NotNull private final Consumer<Integer> mySetSelectedThread;
  @NotNull private final AspectModel<CpuProfilerAspect> myAspect;
  private final Stage myStage;

  public CpuThreadsView(final CpuThreadsModel threadStates,
                        final UpdatableManager updatableManager,
                        final StudioProfilers studioProfilers,
                        final AxisComponentModel timeAxisGuide,
                        final Supplier<Integer> getSelectedThread,
                        final Consumer<Integer> setSelectedThread,
                        final AspectModel<CpuProfilerAspect> aspect,
                        final Stage stage) {
    myThreadStates = threadStates;
    myUpdatableManager = updatableManager;
    myStudioProfilers = studioProfilers;
    myTimeAxisGuide = timeAxisGuide;
    myGetSelectedThread = getSelectedThread;
    mySetSelectedThread = setSelectedThread;
    myAspect = aspect;
    myStage = stage;
    myThreads = new DragAndDropList<>(myThreadStates);
    myPanel = createHideablePanel();
    setupListeners();
    myThreads.setBorder(null);
    myThreads.setCellRenderer(new ThreadCellRenderer(myThreads, myUpdatableManager));
    myThreads.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // TODO(b/62447834): Make a decision on how we want to handle thread selection.
    myThreads.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    // |myPanel| does not receive any mouse events, because all mouse events are consumed by |myThreads|.
    // We're dispatching them manually, so that |CpuProfilerStageView| could register CPU mouse events
    // directly into the top-level component (i.e to |myPanel|) instead of its child.
    DelegateMouseEventHandler.delegateTo(myPanel)
                             .installListenerOn(myThreads)
                             .installMotionListenerOn(myThreads);

    myPanel.addStateChangedListener((actionEvent) ->
                                      myStudioProfilers.getIdeServices().getFeatureTracker().trackToggleCpuThreadsHideablePanel()
    );
    myObserver = new AspectObserver();
    myAspect.addDependency(myObserver)
      .onChange(CpuProfilerAspect.SELECTED_THREADS, this::updateThreadSelection);
  }

  @VisibleForTesting
  public CpuThreadsView(@NotNull CpuProfilerStage stage) {
    this(stage.getThreadStates(), stage.getUpdatableManager(),
         stage.getStudioProfilers(), stage.getTimeAxisGuide(),
         stage::getSelectedThread, stage::setSelectedThread, stage.getAspect(), stage);
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  @NotNull
  private HideablePanel createHideablePanel() {
    // Add AxisComponent only to scrollable section of threads list.
    AxisComponent axisComponent = new AxisComponent(myTimeAxisGuide, AxisComponent.AxisOrientation.BOTTOM, true);
    axisComponent.setShowAxisLine(false);
    axisComponent.setShowLabels(false);
    axisComponent.setHideTickAtMin(true);
    axisComponent.setMarkerColor(ProfilerColors.CPU_AXIS_GUIDE_COLOR);
    final JPanel threads = new JPanel(new TabularLayout("*", "*"));

    final HideablePanel threadsPanel = new HideablePanel.Builder("THREADS", threads)
      .setShowSeparator(false)
      .setClickableComponent(HideablePanel.ClickableComponent.TITLE)
      .setIconTextGap(ProfilerLayout.CPU_HIDEABLE_PANEL_TITLE_ICON_TEXT_GAP)
      .setTitleLeftPadding(ProfilerLayout.CPU_HIDEABLE_PANEL_TITLE_LEFT_PADDING)
      .build();

    CpuListScrollPane scrollingThreads = new CpuListScrollPane(myThreads, threadsPanel);
    scrollingThreads.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        axisComponent.setMarkerLengths(scrollingThreads.getHeight(), 0);
      }
    });

    threads.add(axisComponent, new TabularLayout.Constraint(0, 0));
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
    CpuThreadsModel model = myThreadStates;

    myThreads.addListSelectionListener((e) -> {
      int selectedIndex = myThreads.getSelectedIndex();
      if (selectedIndex >= 0) {
        CpuThreadsModel.RangedCpuThread thread = model.getElementAt(selectedIndex);
        if (myGetSelectedThread.get() != thread.getThreadId()) {
          mySetSelectedThread.accept(thread.getThreadId());
          myStudioProfilers.getIdeServices().getFeatureTracker().trackSelectThread();
        }
      }
      else {
        mySetSelectedThread.accept(CpuThreadsModel.NO_THREAD);
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

    MouseAdapter adapter = new ProfilerTooltipMouseAdapter(myStage,
                                                           () -> new CpuThreadsTooltip(myStudioProfilers.getTimeline())) {
      @Override
      public void mouseMoved(MouseEvent e) {
        super.mouseMoved(e);
        handleMoved(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        super.mouseEntered(e);
        myLastHoveredRow = -1;
        handleMoved(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        repaintLastHoveredRow();
        myLastHoveredRow = -1;
        super.mouseExited(e);
      }

      private void handleMoved(MouseEvent e) {
        repaintLastHoveredRow();

        myLastHoveredRow = myThreads.locationToIndex(e.getPoint());
        if (myLastHoveredRow != -1) {
          repaintLastHoveredRow();
          CpuThreadsModel.RangedCpuThread model = myThreads.getModel().getElementAt(myLastHoveredRow);
          if (myStage.getTooltip() instanceof CpuThreadsTooltip) {
            CpuThreadsTooltip tooltip = (CpuThreadsTooltip)myStage.getTooltip();
            tooltip.setThread(model.getName(), model.getStateSeries());
          }
        }
      }

      private void repaintLastHoveredRow() {
        Rectangle cellBounds = myThreads.getCellBounds(myLastHoveredRow, myLastHoveredRow);
        if (cellBounds != null) {
          myPanel.repaint(SwingUtilities.convertRectangle(myThreads, cellBounds, myPanel));
        }
      }
    };
    myThreads.addMouseListener(adapter);
    myThreads.addMouseMotionListener(adapter);
  }

  /**
   * Selects a thread in the list whose ID matches the one set in the model.
   */
  private void updateThreadSelection() {
    if (myGetSelectedThread.get() == CpuThreadsModel.NO_THREAD) {
      myThreads.clearSelection();
      return;
    }

    // Select the thread which has its tree displayed in capture panel in the threads list
    for (int i = 0; i < myThreads.getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = myThreads.getModel().getElementAt(i);
      if (myGetSelectedThread.get() == thread.getThreadId()) {
        myThreads.setSelectedIndex(i);
        break;
      }
    }
  }
}
