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
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.JComboBoxView;
import com.android.tools.profilers.ViewBinder;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.*;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.profilers.ProfilerLayout.*;

public class CpuCaptureView {
  // Note the order of the values in the map defines the order of the tabs in UI.
  private static final Map<CaptureModel.Details.Type, String> DEFAULT_TAB_NAMES = ImmutableMap.of(
    CaptureModel.Details.Type.CALL_CHART, "Call Chart",
    CaptureModel.Details.Type.FLAME_CHART, "Flame Chart",
    CaptureModel.Details.Type.TOP_DOWN, "Top Down",
    CaptureModel.Details.Type.BOTTOM_UP, "Bottom Up");

  // For Atrace captures names from this map will be used in place of default tab names.
  private static final Map<CaptureModel.Details.Type, String> ATRACE_TAB_NAMES = ImmutableMap.of(
    CaptureModel.Details.Type.CALL_CHART, "Trace Events");

  // Some of the tab names may be replaced. This list defines the currently active tab names as
  private final Map<CaptureModel.Details.Type, String> myTabs = new LinkedHashMap<>(DEFAULT_TAB_NAMES);

  private static final Map<CaptureModel.Details.Type, Consumer<FeatureTracker>> CAPTURE_TRACKERS = ImmutableMap.of(
    CaptureModel.Details.Type.TOP_DOWN, FeatureTracker::trackSelectCaptureTopDown,
    CaptureModel.Details.Type.BOTTOM_UP, FeatureTracker::trackSelectCaptureBottomUp,
    CaptureModel.Details.Type.CALL_CHART, FeatureTracker::trackSelectCaptureCallChart,
    CaptureModel.Details.Type.FLAME_CHART, FeatureTracker::trackSelectCaptureFlameChart
  );

  @NotNull
  private final CpuProfilerStageView myView;

  private final JPanel myPanel;

  private final CommonTabbedPane myTabsPanel;

  @NotNull
  private FilterComponent myFilterComponent;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners.
  // Previously, we were creating a CaptureDetailsView temporarily and grabbing its UI
  // component only. However, in the case of subclass TreeChartView that contains an
  // AspectObserver, which fires events. If that gets cleaned up early, our UI loses some
  // useful events.
  @SuppressWarnings("FieldCanBeLocal")
  @Nullable
  private CaptureDetailsView myDetailsView;

  @NotNull
  private final ViewBinder<CpuProfilerStageView, CaptureModel.Details, CaptureDetailsView> myBinder;

  public CpuCaptureView(@NotNull CpuProfilerStageView view) {
    myView = view;
    myTabsPanel = new CommonTabbedPane();
    JComboBox<ClockType> clockTypeCombo = new ComboBox<>();
    JComboBoxView clockTypes =
      new JComboBoxView<>(clockTypeCombo, view.getStage().getAspect(), CpuProfilerAspect.CLOCK_TYPE,
                          view.getStage()::getClockTypes, view.getStage()::getClockType, view.getStage()::setClockType);
    clockTypes.bind();
    clockTypeCombo.setRenderer(new ClockTypeCellRenderer());
    CpuCapture capture = myView.getStage().getCapture();
    clockTypeCombo.setEnabled(capture != null && capture.isDualClock());

    if (capture != null && capture.getType() == CpuProfiler.CpuProfilerType.ATRACE) {
      myTabs.putAll(ATRACE_TAB_NAMES);
    }
    for (String label : myTabs.values()) {
      myTabsPanel.addTab(label, new JPanel(new BorderLayout()));
    }
    myTabsPanel.addChangeListener(this::setCaptureDetailToTab);
    myTabsPanel.setOpaque(false);
    // TOOLBAR_HEIGHT - 1, so the bottom border of the parent is visible.
    myPanel = new JPanel(new TabularLayout("*,Fit-", (TOOLBAR_HEIGHT - 1) + "px,*"));
    JPanel toolbar = new JPanel(createToolbarLayout());
    toolbar.add(clockTypeCombo);
    toolbar.add(myView.getSelectionTimeLabel());
    myFilterComponent = new FilterComponent(FILTER_TEXT_FIELD_WIDTH, FILTER_TEXT_HISTORY_SIZE, FILTER_TEXT_FIELD_TRIGGER_DELAY_MS);
    if (view.getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isCpuCaptureFilterEnabled()) {
      CommonToggleButton filterButton = FilterComponent.createFilterToggleButton();
      toolbar.add(new FlatSeparator());
      toolbar.add(filterButton);

      myFilterComponent.addOnFilterChange((pattern, model) -> myView.getStage().setCaptureFilter(pattern, model));
      myFilterComponent.setVisible(false);
      myFilterComponent.setBorder(DEFAULT_BOTTOM_BORDER);
      FilterComponent.configureKeyBindingAndFocusBehaviors(myPanel, myFilterComponent, filterButton);
    }

    myPanel.add(toolbar, new TabularLayout.Constraint(0, 1));
    myPanel.add(myTabsPanel, new TabularLayout.Constraint(0, 0, 2, 2));

    myBinder = new ViewBinder<>();
    myBinder.bind(CaptureModel.TopDown.class, TreeDetailsView.TopDownDetailsView::new);
    myBinder.bind(CaptureModel.BottomUp.class, TreeDetailsView.BottomUpDetailsView::new);
    myBinder.bind(CaptureModel.CallChart.class, ChartDetailsView.CallChartDetailsView::new);
    myBinder.bind(CaptureModel.FlameChart.class, ChartDetailsView.FlameChartDetailsView::new);
    updateView();
  }

  public void updateView() {
    // Clear the content of all the tabs
    for (Component tab : myTabsPanel.getComponents()) {
      // In the constructor, we make sure to use JPanel as root components of the tabs.
      assert tab instanceof JPanel;
      ((JPanel)tab).removeAll();
    }
    JComponent filterComponent = myFilterComponent;
    boolean searchHasFocus = filterComponent.isAncestorOf(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    if (filterComponent.getParent() != null) {
      filterComponent.getParent().remove(filterComponent);
    }

    CaptureModel.Details details = myView.getStage().getCaptureDetails();
    if (details == null) {
      return;
    }

    // Update the current selected tab
    String detailsTypeString = myTabs.get(details.getType());
    int currentTabIndex = myTabsPanel.getSelectedIndex();
    if (currentTabIndex < 0 || !myTabsPanel.getTitleAt(currentTabIndex).equals(detailsTypeString)) {
      for (int i = 0; i < myTabsPanel.getTabCount(); ++i) {
        if (myTabsPanel.getTitleAt(i).equals(detailsTypeString)) {
          myTabsPanel.setSelectedIndex(i);
          break;
        }
      }
    }

    // Update selected tab content. As we need to update the content of the tabs dynamically,
    // we use a JPanel (set on the constructor) to wrap the content of each tab's content.
    // This is required because JBTabsImpl doesn't behave consistently when setting tab's component dynamically.
    JPanel selectedTab = (JPanel)myTabsPanel.getSelectedComponent();
    myDetailsView = myBinder.build(myView, details);
    selectedTab.add(filterComponent, BorderLayout.NORTH);
    selectedTab.add(myDetailsView.getComponent(), BorderLayout.CENTER);
    // We're replacing the content by removing and adding a new component.
    // JComponent#removeAll doc says that we should revalidate if it is already visible.
    selectedTab.revalidate();

    // the searchComponent gets re-added to the selected tab component after filtering changes, so reset the focus here.
    if (searchHasFocus) {
      myFilterComponent.requestFocusInWindow();
    }
  }

  private void setCaptureDetailToTab(ChangeEvent event) {
    CaptureModel.Details.Type type = null;
    if (myTabsPanel.getSelectedIndex() >= 0) {
      String tabTitle = myTabsPanel.getTitleAt(myTabsPanel.getSelectedIndex());
      for (Map.Entry<CaptureModel.Details.Type, String> entry : myTabs.entrySet()) {
        if (tabTitle.equals(entry.getValue())) {
          type = entry.getKey();
        }
      }
    }
    myView.getStage().setCaptureDetails(type);

    // TODO: Move this logic into setCaptureDetails later. Right now, if we do it, we track the
    // event several times instead of just once after taking a capture. setCaptureDetails should
    // probably have a guard condition.
    FeatureTracker tracker = myView.getStage().getStudioProfilers().getIdeServices().getFeatureTracker();
    CAPTURE_TRACKERS.getOrDefault(type, featureTracker -> {
    }).accept(tracker);
  }

  private static Logger getLog() {
    return Logger.getInstance(CpuCaptureView.class);
  }

  public JComponent getComponent() {
    return myPanel;
  }


  private static class ClockTypeCellRenderer extends ListCellRendererWrapper<ClockType> {
    @Override
    public void customize(JList list,
                          ClockType value,
                          int index,
                          boolean selected,
                          boolean hasFocus) {
      switch (value) {
        case GLOBAL:
          setText("Wall Clock Time");
          break;
        case THREAD:
          setText("Thread Time");
          break;
        default:
          getLog().warn("Unexpected clock type received.");
      }
    }
  }
}
