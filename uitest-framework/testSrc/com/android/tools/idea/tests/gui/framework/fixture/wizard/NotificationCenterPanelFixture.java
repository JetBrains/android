/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.wizard;

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.components.labels.LinkLabel;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JEditorPane;
import org.fest.swing.core.matcher.JLabelMatcher;

public class NotificationCenterPanelFixture {
  private final IdeFrameFixture myIdeFrameFixture;
  private final Container notificationPanel;

  public static NotificationCenterPanelFixture find(IdeFrameFixture ideFrame, String title) {
    // Tries to locate any notification present on the editor or ide frame using the Header Text/Title
    // Regular expression or String can be used for matching
    Container notificationPanel = ideFrame.robot().finder().find(JLabelMatcher.withText(title)).getParent();
    return new NotificationCenterPanelFixture(ideFrame, notificationPanel);
  }

  private NotificationCenterPanelFixture (IdeFrameFixture ideFrame, Container target) {
    myIdeFrameFixture = ideFrame;
    notificationPanel = target;
  }

  public void clickLabel() {
    // When the link is placed with the JEditorPane
    Component label = myIdeFrameFixture.robot().finder()
      .find(notificationPanel, Matchers.byType(JEditorPane.class));
    myIdeFrameFixture.robot()
      .click(label);
  }

  public void clickLinkLabel() {
    // When there is specified link label to click.
    LinkLabel label = myIdeFrameFixture.robot().finder()
      .find(notificationPanel, Matchers.byType(LinkLabel.class));
    myIdeFrameFixture.robot()
      .click(label);
  }

  public boolean isVisible() {
    return notificationPanel.isVisible();
  }

  public Container target() {
    return notificationPanel;
  }
}
