/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.profilers.LiveDataView;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import java.awt.event.MouseListener;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class LiveCpuUsageView extends LiveDataView<LiveCpuUsageModel> {
  public final JComponent myComponent;
  private final DetailedCpuChart myDetailedCpuChart;
  private final LiveCpuUsageModel myAllocationModel;
  private final TooltipModel myCpuTooltipModel;
  private final StudioProfilersView myProfilersView;

  public LiveCpuUsageView(@NotNull StudioProfilersView profilersView,
                          @NotNull LiveCpuUsageModel allocationModel) {
    super(allocationModel);
    myProfilersView = profilersView;
    myAllocationModel = allocationModel;
    myDetailedCpuChart = new DetailedCpuChart(profilersView, allocationModel);

    TabularLayout topPanelLayout = new TabularLayout("*", "*,Fit-");
    myComponent = new JPanel(topPanelLayout);
    myComponent.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myCpuTooltipModel = myAllocationModel.getTooltip();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public JComponent getUsageTooltipComponent() {
    return this.myDetailedCpuChart.getUsageView();
  }

  /**
   * Helper function to register LiveAllocation components on tooltips. This function is responsible for setting the
   * active tooltip on the stage when a mouse enters the desired component.
   *
   * @param binder
   * @param rangeTooltipComponent
   * @param stage
   */
  @Override
  public void registerTooltip(ViewBinder<StageView, TooltipModel, TooltipView> binder,
                              @NotNull RangeTooltipComponent rangeTooltipComponent,
                              Stage stage) {
    binder.bind(CpuProfilerStageCpuUsageTooltip.class, CpuProfilerStageCpuUsageTooltipView::new);
    binder.bind(CpuThreadsTooltip.class, (stageView, tooltip) -> new CpuThreadsTooltipView(stageView.getComponent(), tooltip));
    rangeTooltipComponent.registerListenersOn(getUsageTooltipComponent());
    MouseListener listener = new ProfilerTooltipMouseAdapter(stage, () -> myCpuTooltipModel);
    getUsageTooltipComponent().addMouseListener(listener);
    // for CPU threads, listener is already set in the CPUThreads.

    myProfilersView.installCommonMenuItems(myDetailedCpuChart.getUsageView());
    myProfilersView.installCommonMenuItems(myDetailedCpuChart.getThreadsView().getComponent());
  }

  @Override
  public void populateUi(RangeTooltipComponent tooltipComponent) {
    myComponent.add(myDetailedCpuChart.createCpuDetailsPanel(2, tooltipComponent), new TabularLayout.Constraint(0, 0));
  }
}
