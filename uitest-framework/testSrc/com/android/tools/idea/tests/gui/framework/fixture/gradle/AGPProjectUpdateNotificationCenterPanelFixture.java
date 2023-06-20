/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Lists;
import com.intellij.ui.components.labels.LinkLabel;
import java.awt.Container;
import java.util.List;
import javax.swing.JLabel;

public class AGPProjectUpdateNotificationCenterPanelFixture {
  private final IdeFrameFixture myIdeFrameFixture;
  private final Container targetPanel;
  private final LinkLabel labelToClick;
  static final String NOTIFICATION_HEADER = "Project update recommended";
  static final String LINK_LABEL_TEXT = "Start AGP Upgrade Assistant";

  public static AGPProjectUpdateNotificationCenterPanelFixture find(IdeFrameFixture ideFrame) {
    List <LinkLabel> linkLabels = Lists.newArrayList(ideFrame.robot().finder().findAll(ideFrame.target(), Matchers.byType(LinkLabel.class)));
    if (linkLabels.size() > 0) {
      for (LinkLabel label : linkLabels) {
        if (label.getText() != null && label.getText().equalsIgnoreCase(LINK_LABEL_TEXT)) {
          return new AGPProjectUpdateNotificationCenterPanelFixture(ideFrame, label.getFocusCycleRootAncestor(), label);
        }
      }
    }
    throw new AssertionError("Unable to find the Project Update Notification center panel with text " + NOTIFICATION_HEADER);
  }

  private AGPProjectUpdateNotificationCenterPanelFixture (IdeFrameFixture ideFrame, Container target, LinkLabel label) {
    myIdeFrameFixture = ideFrame;
    targetPanel = target;
    labelToClick = label;
  }

  public boolean notificationIsShowing() {
    List<JLabel> notificationLabels = Lists.newArrayList(myIdeFrameFixture.robot().finder().findAll(targetPanel, Matchers.byType(JLabel.class)));
    if (notificationLabels.size() > 0) {
      for (JLabel label : notificationLabels) {
        if (label.getText() != null && label.getText().contains(NOTIFICATION_HEADER) && label.isVisible()) {
          return true;
        }
      }
    }
    return false;
  }

  public void clickStartUpgradeAssistant() {
    myIdeFrameFixture.robot().click(labelToClick);
  }
}


