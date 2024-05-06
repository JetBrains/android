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

import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.RangeSelectionComponent;
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.adtui.stdui.DefaultContextMenuItem;
import com.android.tools.inspectors.common.ui.ContextMenuInstaller;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.LiveDataView;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.sessions.SessionAspect;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class LiveMemoryFootprintView extends LiveDataView<LiveMemoryFootprintModel> {
  public final DetailedMemoryChart myDetailedMemoryChart;
  public final JComponent myComponent;
  private final TooltipModel myMemoryUsageTooltip;
  private final LiveMemoryFootprintModel myMemoryFootprintModel;
  private final StudioProfilersView myProfilersView;
  private final JButton myForceGarbageCollectionButton;
  private final DefaultContextMenuItem myForceGarbageCollectionAction;
  private final DurationDataRenderer<GcDurationData> gcDurationDataRenderer;
  private final GarbageCollectionComponent myGarbageCollectionComponent;

  public LiveMemoryFootprintView(@NotNull StudioProfilersView profilersView,
                                 @NotNull LiveMemoryFootprintModel memoryFootprintModel) {

    super(memoryFootprintModel);
    myMemoryFootprintModel = memoryFootprintModel;
    myProfilersView = profilersView;
    myDetailedMemoryChart = new DetailedMemoryChart(myMemoryFootprintModel.getDetailedMemoryUsage(),
                                                    myMemoryFootprintModel.getLegends(),
                                                    myMemoryFootprintModel.getTimeline(),
                                                    myMemoryFootprintModel.getMemoryAxis(),
                                                    myMemoryFootprintModel.getObjectAxis(),
                                                    myMemoryFootprintModel.getRangeSelectionModel(),
                                                    new JPanel(),
                                                    profilersView.getComponent(),
                                                    myMemoryFootprintModel.isLiveAllocationTrackingReady(),
                                                    this::shouldShowTooltip);
    myGarbageCollectionComponent = new GarbageCollectionComponent();
    TabularLayout topPanelLayout = new TabularLayout("*", "*,Fit-");
    myComponent = new JPanel(topPanelLayout);
    myComponent.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myMemoryUsageTooltip = myMemoryFootprintModel.getTooltip();

    myForceGarbageCollectionButton = myGarbageCollectionComponent.makeGarbageCollectionButton(
      memoryFootprintModel.getMemoryDataProvider(), profilersView.getStudioProfilers());

    myForceGarbageCollectionAction = myGarbageCollectionComponent.makeGarbageCollectionAction(
      myMemoryFootprintModel.getStudioProfilers(), myForceGarbageCollectionButton, getComponent());
    myForceGarbageCollectionButton.setToolTipText(myForceGarbageCollectionAction.getDefaultToolTipText());

    gcDurationDataRenderer = myGarbageCollectionComponent.makeGcDurationDataRenderer(
      memoryFootprintModel.getDetailedMemoryUsage(), memoryFootprintModel.getTooltipLegends());

    // Register the render, so that when the garbage collection icon is clicked,
    // the icon get displayed in overlay panel above timeline
    myDetailedMemoryChart.registerRenderer(getGcDurationDataRenderer());
  }

  @VisibleForTesting
  DurationDataRenderer<GcDurationData> getGcDurationDataRenderer() {
    return gcDurationDataRenderer;
  }

  @VisibleForTesting
  public JButton getGarbageCollectionButton() {
      return myForceGarbageCollectionButton;
  }

  @VisibleForTesting
  private DefaultContextMenuItem getGarbageCollectionAction() {
    return myForceGarbageCollectionAction;
  }

  public Boolean shouldShowTooltip() {
    return true;
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  private RangeSelectionComponent getRangeSelectionComponent() {
    return this.myDetailedMemoryChart.getRangeSelectionComponent();
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
    buildContextMenu();

    Runnable onSessionChanged = () -> myGarbageCollectionComponent.updateGcButton(myMemoryFootprintModel.getStudioProfilers(),
                                                                                  myForceGarbageCollectionButton);
    // On session change, update gc button
    myMemoryFootprintModel.getStudioProfilers().getSessionsManager().addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION, onSessionChanged);
  }

  private void buildContextMenu() {
    IdeProfilerComponents ideProfilerComponents = myProfilersView.getIdeProfilerComponents();
    ContextMenuInstaller contextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();
    contextMenuInstaller.installGenericContextMenu(getRangeSelectionComponent(), ContextMenuItem.SEPARATOR);
    contextMenuInstaller.installGenericContextMenu(getRangeSelectionComponent(), getGarbageCollectionAction());
    contextMenuInstaller.installGenericContextMenu(getRangeSelectionComponent(), ContextMenuItem.SEPARATOR);
    myProfilersView.installCommonMenuItems(getRangeSelectionComponent());
  }

  /**
   * To get toolbar icons specific to the data view.
   * @return JComponent
   */
  public JComponent getToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(createToolbarLayout());
    panel.add(toolbar, BorderLayout.WEST);
    toolbar.removeAll();
    toolbar.add(myForceGarbageCollectionButton);
    myGarbageCollectionComponent.updateGcButton(myMemoryFootprintModel.getStudioProfilers(),
                                                myForceGarbageCollectionButton);
    return panel;
  }

  public JComponent getTooltipComponent() {
    return this.myDetailedMemoryChart.getRangeSelectionComponent();
  }
}
