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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.profilers.CloseButton;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Detailed information view of energy duration, for wake locks, alarms, etc.
 */
public class EnergyDetailsView extends JPanel {

  @NotNull private final EnergyCallstackView myCallstackView;
  @NotNull private final EnergyDetailsOverview myDetailsOverview;

  public EnergyDetailsView(@NotNull EnergyProfilerStageView stageView) {
    super(new BorderLayout());
    // Create 2x2 pane
    //     * Fit
    // Fit _ _
    // *   _ _
    //
    // where main contents span the whole area and a close button fits into the top right
    JPanel rootPanel = new JPanel(new TabularLayout("*,Fit", "Fit,*"));

    CommonTabbedPane myTabsPanel = new CommonTabbedPane();
    myDetailsOverview = new EnergyDetailsOverview(stageView);
    myTabsPanel.addTab("Details", myDetailsOverview);

    myCallstackView = new EnergyCallstackView(stageView);
    myTabsPanel.add("Callstack", myCallstackView);

    CloseButton closeButton = new CloseButton(e -> stageView.getStage().setSelectedDuration(null));
    rootPanel.add(closeButton, new TabularLayout.Constraint(0, 1));
    rootPanel.add(myTabsPanel, new TabularLayout.Constraint(0, 0, 2, 2));
    add(rootPanel);
  }

  /**
   * Set the details view for a specific duration, if given {@code duration} is {@code null}, this clears the view and close it.
   */
  public void setDuration(@Nullable EventDuration duration) {
    setBackground(JBColor.background());
    setVisible(duration != null && !duration.getEventList().isEmpty());
    myDetailsOverview.setDuration(duration);
    myCallstackView.setDuration(duration);
    revalidate();
    repaint();
  }
}
