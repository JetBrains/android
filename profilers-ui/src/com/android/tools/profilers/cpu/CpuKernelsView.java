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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.cpu.atrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo;
import com.android.tools.profilers.cpu.capturedetails.CaptureModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Creates a view containing a {@link HideablePanel} composed by a {@link CpuListScrollPane} displaying a list of CPUs. Each row
 * represents a core found in an atrace file and is composed by a {@link com.android.tools.adtui.chart.statechart.StateChart} whose data are
 * the list of {@link CpuThreadSliceInfo} associated with that core.
 */
public class CpuKernelsView extends JBList<CpuKernelModel.CpuState> {

  @NotNull
  private final HideablePanel myPanel;

  @NotNull
  private final CpuProfilerStage myStage;

  @NotNull
  private final CpuThreadsView myThreads;

  // TODO(b/110766649): Do not expose the parent only to capture mouse events.
  public CpuKernelsView(@NotNull CpuProfilerStage stage, @NotNull CpuThreadsView threadsView, @NotNull JPanel parent) {
    super(stage.getCpuKernelModel());
    myStage = stage;
    myThreads = threadsView;
    myPanel = createKernelsPanel(parent);

    setupListeners();
    setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    setCellRenderer(new CpuKernelCellRenderer(myStage.getStudioProfilers().getIdeServices().getFeatureConfig(),
                                              myStage.getStudioProfilers().getSession().getPid(),
                                              this, threadsView.getThreads()));
  }

  @NotNull
  public HideablePanel getPanel() {
    return myPanel;
  }

  private void setupListeners() {
    // Handle selection.
    addListSelectionListener((e) -> cpuKernelRunningStateSelected(getModel()));
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        cpuKernelRunningStateSelected(getModel());
        myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectCpuKernelElement();
      }
    });

    // Handle Tooltip
    addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuKernelTooltip(myStage)));
    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = locationToIndex(e.getPoint());
        if (row != -1) {
          CpuKernelModel.CpuState model = getModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuKernelTooltip) {
            CpuKernelTooltip tooltip = (CpuKernelTooltip)myStage.getTooltip();
            tooltip.setCpuSeries(model.getCpuId(), model.getSeries());
          }
        }
      }
    });
  }

  @NotNull
  private HideablePanel createKernelsPanel(@NotNull JPanel parent) {
    // Create hideable panel for CPU list.
    HideablePanel kernelsPanel = new HideablePanel.Builder("KERNEL", new CpuListScrollPane(this, parent))
      .setShowSeparator(false)
      // We want to keep initially expanded to false because the kernel layout is set to "Fix" by default. As such when
      // we later change the contents to have elements and expand the view we also want to trigger the StateChangedListener below
      // to properly set the layout to be expanded. If we set initially expanded to true, then the StateChangedListener will never
      // get triggered and we will not update our layout.
      .setInitiallyExpanded(false)
      .setClickableComponent(HideablePanel.ClickableComponent.TITLE)
      .build();

    // Hide CPU panel by default
    kernelsPanel.setVisible(false);

    // Clear border set by default on the hideable panel.
    kernelsPanel.setBorder(JBUI.Borders.empty());
    kernelsPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    kernelsPanel.addStateChangedListener(
      (e) -> myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackToggleCpuKernelHideablePanel());
    return kernelsPanel;
  }

  /**
   * When a running state is selected from the CPU {@link JBList}, this method handles finding and selecting the proper thread as well as
   * triggering the feature tracker to register the thread selection.
   */
  private void cpuKernelRunningStateSelected(@NotNull ListModel<CpuKernelModel.CpuState> cpuModel) {
    int selectedIndex = getSelectedIndex();
    if (selectedIndex < 0) {
      myStage.setSelectedThread(CaptureModel.NO_THREAD);
      return;
    }
    CpuKernelModel.CpuState state = cpuModel.getElementAt(selectedIndex);
    Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();
    List<SeriesData<CpuThreadSliceInfo>> process = state.getModel().getSeries().get(0).getDataSeries().getDataForXRange(tooltipRange);
    if (process.isEmpty()) {
      return;
    }

    int id = process.get(0).value.getId();
    CpuThreadsModel threadsModel = myStage.getThreadStates();
    for (int i = 0; i < myThreads.getThreads().getModel().getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = threadsModel.getElementAt(i);
      if (id == thread.getThreadId()) {
        myStage.setSelectedThread(thread.getThreadId());
        myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectThread();
        break;
      }
    }
  }
}
