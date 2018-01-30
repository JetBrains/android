/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.network.details;

import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.profilers.CloseButton;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.network.NetworkProfilerStageView;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * View to display a single network request and its detailed information.
 */
public class ConnectionDetailsView extends JPanel {
  @NotNull
  private final NetworkProfilerStageView myStageView;

  @NotNull
  private final CommonTabbedPane myTabsPanel;

  @NotNull
  private final List<TabContent> myTabs = new ArrayList<>();

  public ConnectionDetailsView(@NotNull NetworkProfilerStageView stageView) {
    super(new BorderLayout());
    myStageView = stageView;
    // Create 2x2 pane
    //     * Fit
    // Fit _ _
    // *   _ _
    //
    // where main contents span the whole area and a close button fits into the top right
    JPanel rootPanel = new JPanel(new TabularLayout("*,Fit", "Fit,*"));

    myTabsPanel = new CommonTabbedPane();

    populateTabs();

    myTabsPanel.addChangeListener(e -> {
      // Repaint required on tab change or else close button sometimes disappears (seen on Mac)
      repaint();
      trackActiveTab();
    });

    CloseButton closeButton = new CloseButton(e -> myStageView.getStage().setSelectedConnection(null));
    rootPanel.add(closeButton, new TabularLayout.Constraint(0, 1));
    rootPanel.add(myTabsPanel, new TabularLayout.Constraint(0, 0, 2, 2));

    add(rootPanel);
  }

  private void populateTabs() {
    boolean isRequestPayloadEnabled =
      myStageView.getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isNetworkRequestPayloadEnabled();

    myTabs.add(new OverviewTabContent(myStageView.getStage().getStudioProfilers().getIdeServices().getFeatureConfig(),
                                      myStageView.getIdeComponents(), myStageView.getStage().getConnectionsModel()));

    if (isRequestPayloadEnabled) {
      myTabs.add(new ResponseTabContent(myStageView.getIdeComponents(), myStageView.getStage().getConnectionsModel()));
      myTabs.add(new RequestTabContent(myStageView.getIdeComponents(), myStageView.getStage().getConnectionsModel()));
    }
    else {
      myTabs.add(new HeadersTabContent());
    }

    myTabs.add(new CallStackTabContent(myStageView.getStage().getConnectionsModel(),
                                       myStageView.getIdeComponents().createStackView(myStageView.getStage().getStackTraceModel())));

    for (TabContent tab : myTabs) {
      myTabsPanel.addTab(tab.getTitle(), tab.getIcon(), tab.getComponent());
    }
  }

  private void trackActiveTab() {
    Optional<TabContent> selected = myTabs.stream().filter(t -> t.getComponent() == myTabsPanel.getSelectedComponent()).findFirst();
    if (selected.isPresent()) {
      FeatureTracker featureTracker = myStageView.getStage().getStudioProfilers().getIdeServices().getFeatureTracker();
      selected.get().trackWith(featureTracker);
    }
  }

  /**
   * Updates the view to show given data. If {@code httpData} is {@code null}, this clears the view
   * and closes it.
   */
  public void setHttpData(@Nullable HttpData httpData) {
    setBackground(JBColor.background());
    myTabs.forEach(tab -> tab.populateFor(httpData));
    setVisible(httpData != null);
    revalidate();
    repaint();
  }

  @NotNull
  @VisibleForTesting
  List<TabContent> getTabs() {
    return myTabs;
  }
}
