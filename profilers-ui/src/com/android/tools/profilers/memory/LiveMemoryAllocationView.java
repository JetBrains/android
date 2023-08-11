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
package com.android.tools.profilers.memory;

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
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class LiveMemoryAllocationView extends LiveDataView<LiveMemoryAllocationModel> {
  public final DetailedMemoryChart myDetailedMemoryChart;
  public final JComponent myComponent;
  private final TooltipModel myMemoryUsageTooltip;
  private final LiveMemoryAllocationModel myAllocationModel;
  private final StudioProfilersView myProfilersView;

  public LiveMemoryAllocationView(@NotNull StudioProfilersView profilersView,
                                  @NotNull LiveMemoryAllocationModel allocationModel) {

    super(allocationModel);
    myAllocationModel = allocationModel;
    myProfilersView = profilersView;
    myDetailedMemoryChart = new DetailedMemoryChart(myAllocationModel.getDetailedMemoryUsage(),
                                                    myAllocationModel.getLegends(),
                                                    myAllocationModel.getTimeline(),
                                                    myAllocationModel.getMemoryAxis(),
                                                    myAllocationModel.getObjectAxis(),
                                                    myAllocationModel.getRangeSelectionModel(),
                                                    new JPanel(),
                                                    profilersView.getComponent(),
                                                    myAllocationModel.isLiveAllocationTrackingReady(),
                                                    this::shouldShowTooltip);
    TabularLayout topPanelLayout = new TabularLayout("*", "*,Fit-");
    myComponent = new JPanel(topPanelLayout);
    myComponent.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myMemoryUsageTooltip = myAllocationModel.getTooltip();
  }

  public Boolean shouldShowTooltip() {
    return true;
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  /**
   * Helper function to register LiveAllocation components on tooltips. This function is responsible for setting the
   * active tooltip on the stage when a mouse enters the desired component.
   *
   * @param binder
   * @param tooltip
   * @param stage
   */
  @Override
  public void registerTooltip(ViewBinder<StageView, TooltipModel, TooltipView> binder,
                              @NotNull RangeTooltipComponent tooltip,
                              Stage stage) {
    getTooltipComponent()
      .addMouseListener(new ProfilerTooltipMouseAdapter(stage, () -> this.myMemoryUsageTooltip));
    binder.bind(MemoryUsageTooltip.class, MemoryUsageTooltipView::new);
    stage.setTooltip(this.myMemoryUsageTooltip);
    tooltip.registerListenersOn(getTooltipComponent());
  }

  @Override
  public void populateUi(RangeTooltipComponent tooltipComponent) {
    myComponent.add(tooltipComponent,
                    new TabularLayout.Constraint(0, 0, 2, 1));
    myComponent.add(this.myDetailedMemoryChart.makeMonitorPanel(this.myDetailedMemoryChart.getOverlayPanel()),
                    new TabularLayout.Constraint(0, 0));
    myProfilersView.installCommonMenuItems(this.myDetailedMemoryChart.getRangeSelectionComponent());
  }

  public JComponent getTooltipComponent() {
    return this.myDetailedMemoryChart.getRangeSelectionComponent();
  }
}
