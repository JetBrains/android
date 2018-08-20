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

import com.android.tools.adtui.TabularLayout;
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
public class CpuKernelsView {

  @NotNull
  private final HideablePanel myPanel;

  @NotNull
  private final CpuProfilerStage myStage;

  @NotNull
  private final JBList<CpuKernelModel.CpuState> myKernels;

  public CpuKernelsView(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    myKernels = new JBList<>(stage.getCpuKernelModel());
    myPanel = createKernelsPanel();
    setupListeners();
    myKernels.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    myKernels.setCellRenderer(new CpuKernelCellRenderer(myStage, myStage.getStudioProfilers().getIdeServices().getFeatureConfig(),
                                              myStage.getStudioProfilers().getSession().getPid(),
                                              myKernels));
  }

  @NotNull
  public HideablePanel getPanel() {
    return myPanel;
  }

  /* TODO(b/112827411): We don't need to expose the list when refactoring will be done.
     Consumers of CpuKernelsView should be able to register mouse or UI events directly to the top-level component of CpuKernelsView.
   */
  @NotNull
  public JBList<CpuKernelModel.CpuState> getKernels() {
    return myKernels;
  }

  private void setupListeners() {
    // Handle selection.
    myKernels.addListSelectionListener((e) -> cpuKernelRunningStateSelected(myKernels.getModel()));
    myKernels.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        cpuKernelRunningStateSelected(myKernels.getModel());
        myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectCpuKernelElement();
      }
    });

    // Handle Tooltip
    myKernels.addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuKernelTooltip(myStage)));
    myKernels.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = myKernels.locationToIndex(e.getPoint());
        if (row != -1) {
          CpuKernelModel.CpuState model = myKernels.getModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuKernelTooltip) {
            CpuKernelTooltip tooltip = (CpuKernelTooltip)myStage.getTooltip();
            tooltip.setCpuSeries(model.getCpuId(), model.getSeries());
          }
        }
      }
    });
  }

  @NotNull
  private HideablePanel createKernelsPanel() {
    JPanel kernelsContent = new JPanel(new TabularLayout("*", "*"));
    // Create hideable panel for CPU list.
    HideablePanel kernelsPanel = new HideablePanel.Builder("KERNEL", kernelsContent)
      .setShowSeparator(false)
      // We want to keep initially expanded to false because the kernel layout is set to "Fix" by default. As such when
      // we later change the contents to have elements and expand the view we also want to trigger the StateChangedListener below
      // to properly set the layout to be expanded. If we set initially expanded to true, then the StateChangedListener will never
      // get triggered and we will not update our layout.
      .setInitiallyExpanded(false)
      .setClickableComponent(HideablePanel.ClickableComponent.TITLE)
      .build();
    kernelsContent.add(new CpuListScrollPane(myKernels, kernelsPanel), new TabularLayout.Constraint(0, 0));
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
    int selectedIndex = myKernels.getSelectedIndex();
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
    for (int i = 0; i < threadsModel.getSize(); i++) {
      CpuThreadsModel.RangedCpuThread thread = threadsModel.getElementAt(i);
      if (id == thread.getThreadId()) {
        myStage.setSelectedThread(thread.getThreadId());
        myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectThread();
        break;
      }
    }
  }
}
