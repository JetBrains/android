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

import static com.android.tools.profilers.ProfilerFonts.STANDARD_FONT;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.stdui.CloseButton;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBEmptyBorder;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detailed information view of energy duration, for wake locks, alarms, etc.
 */
public class EnergyDetailsView extends JPanel {

  @NotNull private final EnergyCallstackView myCallstackView;
  @NotNull private final EnergyDetailsOverview myDetailsOverview;
  @NotNull private final JLabel myTitleLabel;

  public EnergyDetailsView(@NotNull EnergyProfilerStageView stageView) {
    super(new TabularLayout("*", "Fit-,*"));

    JPanel titlePanel = new JPanel(new BorderLayout());
    titlePanel.setBorder(AdtUiUtils.DEFAULT_BOTTOM_BORDER);
    myTitleLabel = new JLabel();
    myTitleLabel.setFont(STANDARD_FONT);
    myTitleLabel.setBorder(new JBEmptyBorder(6, 10, 6, 3));
    titlePanel.add(myTitleLabel, BorderLayout.WEST);
    CloseButton closeButton = new CloseButton(e -> stageView.getStage().setSelectedDuration(null));
    titlePanel.add(closeButton, BorderLayout.EAST);

    myDetailsOverview = new EnergyDetailsOverview();
    myCallstackView = new EnergyCallstackView(stageView);
    JPanel detailsPanel = new JPanel(new VerticalFlowLayout(0, 0));
    detailsPanel.setBorder(new JBEmptyBorder(7, 10, 0, 10));
    detailsPanel.add(myDetailsOverview);
    detailsPanel.add(AdtUiUtils.createHorizontalSeparator());
    detailsPanel.add(myCallstackView);
    detailsPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myDetailsOverview.setBackground(null);
    myCallstackView.setBackground(null);
    JBScrollPane detailsScrollPane = new JBScrollPane(detailsPanel);
    detailsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    add(titlePanel, new TabularLayout.Constraint(0, 0));
    add(detailsScrollPane, new TabularLayout.Constraint(1, 0));
  }

  /**
   * Set the details view for a specific duration, if given {@code duration} is {@code null}, this clears the view and close it.
   */
  public void setDuration(@Nullable EnergyDuration duration) {
    setBackground(JBColor.background());
    setVisible(duration != null && !duration.getEventList().isEmpty());
    myDetailsOverview.setDuration(duration);
    myCallstackView.setDuration(duration);
    myTitleLabel.setText(duration != null ? duration.getKind().getDisplayName() + " Details" : "");

    revalidate();
    repaint();
  }
}
