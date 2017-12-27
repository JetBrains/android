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
package com.android.tools.profilers;

import com.android.tools.adtui.HtmlLabel;
import com.intellij.ui.JBColor;
import icons.StudioIllustrations;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * View shown if no processes are selected
 */
public class NullMonitorStageView extends StageView<NullMonitorStage> {

  @NotNull
  private NullMonitorStage myStage;

  private HtmlLabel myDisabledMessage;
  private JLabel myTitle;

  public NullMonitorStageView(@NotNull StudioProfilersView profilersView, @NotNull NullMonitorStage stage) {
    super(profilersView, stage);
    myStage = stage;

    JPanel topPanel = new JPanel();
    BoxLayout layout = new BoxLayout(topPanel, BoxLayout.Y_AXIS);
    topPanel.setLayout(layout);

    topPanel.add(Box.createVerticalGlue());
    topPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    JLabel picLabel = new JLabel(StudioIllustrations.Common.DISCONNECT);
    picLabel.setHorizontalAlignment(SwingConstants.CENTER);
    picLabel.setVerticalAlignment(SwingConstants.CENTER);
    picLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    topPanel.add(picLabel);

    myTitle = new JLabel();
    myTitle.setHorizontalAlignment(SwingConstants.CENTER);
    myTitle.setVerticalAlignment(SwingConstants.TOP);
    myTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
    myTitle.setFont(myTitle.getFont().deriveFont(21.0f));
    myTitle.setForeground(new JBColor(0x000000, 0xFFFFFF));
    topPanel.add(myTitle);
    topPanel.add(Box.createRigidArea(new Dimension(1, 15)));

    myDisabledMessage = new HtmlLabel();
    Font font = myTitle.getFont().deriveFont(11.0f);
    HtmlLabel.setUpAsHtmlLabel(myDisabledMessage, font, ProfilerColors.MESSAGE_COLOR);
    topPanel.add(myDisabledMessage);
    topPanel.add(Box.createVerticalGlue());

    getComponent().add(topPanel, BorderLayout.CENTER);

    stage.getAspect().addDependency(this).onChange(NullMonitorStage.Aspect.NULL_MONITOR_TYPE, this::updateTitleAndMessage);
    updateTitleAndMessage();
  }

  private void updateTitleAndMessage() {
    myTitle.setText(myStage.getTitle());
    myDisabledMessage.setText("<html><body><div style='text-align: center;'>" + myStage.getMessage() +
                              " <a href=\"https://developer.android.com/r/studio-ui/about-profilers.html\">Learn More</a></div></body></html>");
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }
}
