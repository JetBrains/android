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

import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_HEIGHT;
import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.FilterComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.JComboBoxView;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuProfilerAspect;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SimpleListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * A base panel that contains all common components of {@link CpuCaptureView}.
 * Subclasses of {@link CapturePane} can have different tab contents.
 */
abstract class CapturePane extends JPanel {
  // Note the order of the values in the map defines the order of the tabs in UI.
  private static final Map<CaptureDetails.Type, String> DEFAULT_TAB_NAMES = ImmutableMap.of(
    CaptureDetails.Type.CALL_CHART, "Call Chart",
    CaptureDetails.Type.FLAME_CHART, "Flame Chart",
    CaptureDetails.Type.TOP_DOWN, "Top Down",
    CaptureDetails.Type.BOTTOM_UP, "Bottom Up");

  // For Atrace captures, names from this map will be used in place of default tab names.
  private static final Map<CaptureDetails.Type, String> ATRACE_TAB_NAMES = ImmutableMap.of(
    CaptureDetails.Type.CALL_CHART, "Trace Events");

  // Some of the tab names may be replaced. This list contains the default tabs
  protected final Map<CaptureDetails.Type, String> myTabs = new LinkedHashMap<>(DEFAULT_TAB_NAMES);

  @NotNull protected final CpuProfilerStageView myStageView;

  @NotNull protected final CommonTabbedPane myTabsPanel;

  /*
   * When {@link FeatureConfig::isCpuCaptureStageEnabled} is true, we populate the recording panel instead of the tabs panel.
   */
  @NotNull private final JPanel myRecordingPanel;

  @NotNull protected final Toolbar myToolbar;

  protected CapturePane(@NotNull CpuProfilerStageView stageView) {
    // TOOLBAR_HEIGHT - 1, so the bottom border of the parent is visible.
    super(new TabularLayout("*,Fit-", (TOOLBAR_HEIGHT - 1) + "px,*"));

    myStageView = stageView;
    myTabsPanel = new CommonTabbedPane();

    // Only used when new capture stage is enabled.
    myRecordingPanel = new JPanel(new BorderLayout());

    myToolbar = new Toolbar(stageView);

    if (myStageView.getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isCpuCaptureStageEnabled()) {
      add(myRecordingPanel, new TabularLayout.Constraint(0, 0, 2, 2));
    }
    else {
      CpuCapture capture = myStageView.getStage().getCapture();
      if (capture != null && capture.getSystemTraceData() != null) {
        myTabs.putAll(ATRACE_TAB_NAMES);
      }

      for (String label : myTabs.values()) {
        myTabsPanel.addTab(label, new JPanel(new BorderLayout()));
      }
      myTabsPanel.setSelectedIndex(0);
      add(myToolbar, new TabularLayout.Constraint(0, 1));
      add(myTabsPanel, new TabularLayout.Constraint(0, 0, 2, 2));
    }
  }

  final void updateView() {
    // If we are using the new capture stage we do not want to enable any of the tabs for the capture pane.
    if (myStageView.getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isCpuCaptureStageEnabled()) {
      // This needs to be done in update view (oppose to the constructor) due to dependencies between the CapturePane and the RecordingPane.
      myRecordingPanel.removeAll();
      populateContent(myRecordingPanel);
      return;
    }
    // Clear the content of all the tabs
    for (Component tab : myTabsPanel.getComponents()) {
      // In the constructor, we make sure to use JPanel as root components of the tabs.
      assert tab instanceof JPanel;
      ((JPanel)tab).removeAll();
    }

    CaptureDetails details = myStageView.getStage().getCaptureDetails();
    if (details != null) {
      // Update the current selected tab
      String tabTitle = myTabs.get(details.getType());
      int currentTabIndex = myTabsPanel.getSelectedIndex();
      if (currentTabIndex < 0 || !myTabsPanel.getTitleAt(currentTabIndex).equals(tabTitle)) {
        for (int i = 0; i < myTabsPanel.getTabCount(); ++i) {
          if (myTabsPanel.getTitleAt(i).equals(tabTitle)) {
            myTabsPanel.setSelectedIndex(i);
            break;
          }
        }
      }
    }
    // We never set to -1, so at this point we should have a selected index.
    assert myTabsPanel.getSelectedIndex() != -1;
    JPanel selectedTab = (JPanel)myTabsPanel.getSelectedComponent();
    populateContent(selectedTab);
    // We're replacing the content by removing and adding a new component.
    // JComponent#removeAll doc says that we should revalidate if it is already visible.
    selectedTab.revalidate();
  }

  abstract void populateContent(@NotNull JPanel panel);

  void disableInteraction() {
    myTabsPanel.setEnabled(false);
    myToolbar.setEnabled(false);
    myRecordingPanel.setEnabled(false);
  }

  /**
   * The toolbar of {@link CpuCaptureView}, e.g it contains the filter button and clock type combo box.
   */
  static class Toolbar extends JPanel {
    @NotNull private final CommonToggleButton myFilterButton;
    @NotNull private final JComboBox<ClockType> myClockType;

    Toolbar(CpuProfilerStageView stageView) {
      super(createToolbarLayout());

      myClockType = new ComboBox<>();
      JComboBoxView clockTypes =
        new JComboBoxView<>(myClockType, stageView.getStage().getAspect(), CpuProfilerAspect.CLOCK_TYPE,
                            stageView.getStage()::getClockTypes, stageView.getStage()::getClockType, stageView.getStage()::setClockType);
      clockTypes.bind();
      myClockType.setRenderer(SimpleListCellRenderer.create("", value ->
        value == ClockType.GLOBAL ? "Wall Clock Time" :
        value == ClockType.THREAD ? "Thread Time" : ""));
      CpuCapture capture = stageView.getStage().getCapture();
      myClockType.setEnabled(capture != null && capture.isDualClock());

      add(myClockType);
      add(stageView.getSelectionTimeLabel());

      myFilterButton = FilterComponent.createFilterToggleButton();

      add(new FlatSeparator());
      add(myFilterButton);
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      myFilterButton.setEnabled(enabled);
      myClockType.setEnabled(enabled);
    }

    @NotNull
    CommonToggleButton getFilterButton() {
      return myFilterButton;
    }
  }
}
