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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.FilterComponent;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.filter.FilterHandler;
import com.android.tools.adtui.model.filter.FilterResult;
import com.android.tools.profilers.ViewBinder;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_TRIGGER_DELAY_MS;
import static com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_WIDTH;
import static com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_HISTORY_SIZE;

/**
 * A {@link CapturePane} that renders the selected {@link CaptureModel.Details}.
 */
class DetailsCapturePane extends CapturePane {
  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners.
  // Previously, we were creating a CaptureDetailsView temporarily and grabbing its UI
  // component only. However, in the case of subclass TreeChartView that contains an
  // AspectObserver, which fires events. If that gets cleaned up early, our UI loses some
  // useful events.
  @SuppressWarnings("FieldCanBeLocal")
  @Nullable
  private CaptureDetailsView myDetailsView;

  @NotNull
  private final FilterComponent myFilterComponent;

  @NotNull
  private final ViewBinder<CpuProfilerStageView, CaptureModel.Details, CaptureDetailsView> myBinder;

  DetailsCapturePane(@NotNull CpuProfilerStageView view) {
    super(view);
    myBinder = new ViewBinder<>();
    myBinder.bind(CaptureModel.TopDown.class, TreeDetailsView.TopDownDetailsView::new);
    myBinder.bind(CaptureModel.BottomUp.class, TreeDetailsView.BottomUpDetailsView::new);
    myBinder.bind(CaptureModel.CallChart.class, ChartDetailsView.CallChartDetailsView::new);
    myBinder.bind(CaptureModel.FlameChart.class, ChartDetailsView.FlameChartDetailsView::new);

    myTabsPanel.addChangeListener(event -> setCaptureDetailToTab());

    myFilterComponent = new FilterComponent(FILTER_TEXT_FIELD_WIDTH, FILTER_TEXT_HISTORY_SIZE, FILTER_TEXT_FIELD_TRIGGER_DELAY_MS);
    if (view.getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isCpuCaptureFilterEnabled()) {
      myFilterComponent.getModel().setFilterHandler(new FilterHandler() {
        @Override
        @NotNull
        protected FilterResult applyFilter(@NotNull Filter filter) {
          myStageView.getStage().setCaptureFilter(filter);
          // TODO: b/110904682 Show match count result for cpu profiler.
          return new FilterResult(0, false);
        }
      });
      myFilterComponent.setVisible(false);
      myFilterComponent.setBorder(DEFAULT_BOTTOM_BORDER);
      FilterComponent.configureKeyBindingAndFocusBehaviors(this, myFilterComponent, myToolbar.getFilterButton());
    }
    updateView();
  }

  @Override
  void populateContent(@NotNull JPanel panel) {
    boolean filterHasFocus = myFilterComponent.isAncestorOf(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    if (myFilterComponent.getParent() != null) {
      myFilterComponent.getParent().remove(myFilterComponent);
    }

    CaptureModel.Details details = myStageView.getStage().getCaptureDetails();
    if (details == null) {
      return;
    }

    myDetailsView = myBinder.build(myStageView, details);
    panel.add(myFilterComponent, BorderLayout.NORTH);
    panel.add(myDetailsView.getComponent(), BorderLayout.CENTER);

    // the filterComponent gets re-added to the selected tab component after filtering changes, so reset the focus here.
    if (filterHasFocus) {
      myFilterComponent.requestFocusInWindow();
    }
  }

  private void setCaptureDetailToTab() {
    String tabTitle = myTabsPanel.getTitleAt(myTabsPanel.getSelectedIndex());
    CaptureModel.Details.Type type = myTabs.entrySet().stream()
                                           .filter(e -> tabTitle.equals(e.getValue()))
                                           .map(e -> e.getKey())
                                           .findFirst()
                                           .orElse(null);
    myStageView.getStage().setCaptureDetails(type);
  }
}
