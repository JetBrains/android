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
import com.android.tools.idea.tests.gui.framework.fixture.wizard.NotificationCenterPanelFixture;
import java.awt.Container;
import javax.swing.JButton;

public class AGPProjectUpdateNotificationCenterPanelFixture {
  private final IdeFrameFixture myIdeFrameFixture;
  private Container upgradeAssistantNotification;

  public static AGPProjectUpdateNotificationCenterPanelFixture find(IdeFrameFixture ideFrame) {
    NotificationCenterPanelFixture upgradeNotification = NotificationCenterPanelFixture.find(ideFrame, ".*Project update recommended.*");
    return new AGPProjectUpdateNotificationCenterPanelFixture(ideFrame, upgradeNotification.target());
  }

  private AGPProjectUpdateNotificationCenterPanelFixture (IdeFrameFixture ideFrame, Container target) {
    myIdeFrameFixture = ideFrame;
    upgradeAssistantNotification = target;
  }

  public boolean notificationIsShowing() {
    return upgradeAssistantNotification.isShowing();
  }

  public void clickStartUpgradeAssistant() {
    JButton openUpgradeAssistantButton = myIdeFrameFixture.robot().finder().findByType(upgradeAssistantNotification, JButton.class);
    myIdeFrameFixture.robot().click(openUpgradeAssistantButton);
  }
}


