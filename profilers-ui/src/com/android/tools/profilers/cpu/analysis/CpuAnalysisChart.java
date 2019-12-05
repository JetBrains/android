/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.FilterComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.filter.FilterHandler;
import com.android.tools.adtui.model.filter.FilterResult;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetailsView;
import com.android.tools.profilers.cpu.capturedetails.ChartDetailsView;
import com.android.tools.profilers.cpu.capturedetails.TreeDetailsView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Analysis tab that displays charts of type {@link CaptureDetailsView}
 */
public class CpuAnalysisChart extends CpuAnalysisTab<CpuAnalysisChartModel<?>> {
  private final StudioProfilersView myProfilersView;
  private final JPanel myCaptureDetailsPanel = new JPanel(new BorderLayout());

  @NotNull
  private final ViewBinder<StudioProfilersView, CaptureDetails, CaptureDetailsView> myBinder;

  public CpuAnalysisChart(@NotNull StudioProfilersView view, @NotNull CpuAnalysisTabModel<?> model) {
    super((CpuAnalysisChartModel<?>)model);
    myProfilersView = view;
    myBinder = new ViewBinder<>();
    myBinder.bind(CaptureDetails.TopDown.class, TreeDetailsView.TopDownDetailsView::new);
    myBinder.bind(CaptureDetails.BottomUp.class, TreeDetailsView.BottomUpDetailsView::new);
    myBinder.bind(CaptureDetails.CallChart.class, ChartDetailsView.CallChartDetailsView::new);
    myBinder.bind(CaptureDetails.FlameChart.class, ChartDetailsView.FlameChartDetailsView::new);

    buildComponents();
  }

  private void buildComponents() {
    setLayout(new TabularLayout("*", "Fit,Fit,*"));

    // Toolbar
    CommonToggleButton filterButton = FilterComponent.createFilterToggleButton();
    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    toolbar.add(filterButton);

    // Filter component
    FilterComponent filterComponent = buildFilterComponent();
    FilterComponent.configureKeyBindingAndFocusBehaviors(this, filterComponent, filterButton);

    // Capture details view
    myCaptureDetailsPanel.add(myBinder.build(myProfilersView, getModel().createDetails()).getComponent(), BorderLayout.CENTER);

    add(toolbar, new TabularLayout.Constraint(0, 0));
    add(filterComponent, new TabularLayout.Constraint(1, 0));
    add(myCaptureDetailsPanel, new TabularLayout.Constraint(2, 0));

    boolean hasAxisComponent = getModel().getDetailsType() == CaptureDetails.Type.FLAME_CHART;
    // For backwards compatibility adding the percentage AxisComponent here. This really should live in the CaptureDetails.Type.FLAME_CHART.
    if (hasAxisComponent) {
      AxisComponent percentAxis = new AxisComponent(getModel().getAxisComponentModel(), AxisComponent.AxisOrientation.BOTTOM);
      percentAxis.setShowAxisLine(true);
      percentAxis.setMinimumSize(new Dimension(0, ProfilerLayout.TIME_AXIS_HEIGHT));
      add(percentAxis, new TabularLayout.Constraint(3, 0));
    }
  }

  private FilterComponent buildFilterComponent() {
    FilterComponent filterComponent = new FilterComponent(
      Filter.EMPTY_FILTER,
      ProfilerLayout.FILTER_TEXT_FIELD_WIDTH,
      ProfilerLayout.FILTER_TEXT_HISTORY_SIZE,
      ProfilerLayout.FILTER_TEXT_FIELD_TRIGGER_DELAY_MS)
      // TODO(b/112703942): Show again when we can completely support this value
      .setMatchCountVisibility(false);
    filterComponent.getModel().setFilterHandler(new FilterHandler() {
      @NotNull
      @Override
      protected FilterResult applyFilter(@NotNull Filter filter) {
        CpuAnalysisChartModel.CaptureDetailsWithFilterResult results = getModel().applyFilterAndCreateDetails(filter);
        // Update capture details view.
        CaptureDetailsView view = myBinder.build(myProfilersView, results.getCaptureDetails());
        myCaptureDetailsPanel.removeAll();
        myCaptureDetailsPanel.add(view.getComponent(), BorderLayout.CENTER);
        // Return filter result.
        return results.getFilterResult();
      }
    });
    filterComponent.setVisible(!filterComponent.getModel().getFilter().isEmpty());
    filterComponent.setBorder(AdtUiUtils.DEFAULT_BOTTOM_BORDER);
    return filterComponent;
  }
}
