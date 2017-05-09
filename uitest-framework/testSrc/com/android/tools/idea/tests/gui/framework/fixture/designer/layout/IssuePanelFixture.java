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
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.error.IssuePanel;
import com.android.tools.idea.uibuilder.error.IssueView;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JLabelFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;

/**
 * Fixture for {@link com.android.tools.idea.uibuilder.error.IssuePanel}
 */
public class IssuePanelFixture extends JPanelFixture {

  private final IssuePanel myIssuePanel;

  public IssuePanelFixture(@Nonnull Robot robot, @NotNull IssuePanel panel) {
    super(robot, panel);
    myIssuePanel = panel;
  }

  public JLabelFixture findIssueWithTitle(String issueTitle) throws NullPointerException {
    return label(Matchers.byText(JLabel.class, issueTitle));
  }

  public IssuePanelFixture clickFixButton() {
    button(Matchers.byText(JButton.class, "Fix")).click();
    return this;
  }

  public boolean hasRenderError() {
    return myIssuePanel.getIssueModel().hasRenderError() && myIssuePanel.getTitleText().matches(".*[Ee]rror.*");
  }

  public boolean containsText(String text) {
    return myIssuePanel.containsErrorWithText(text);
  }

  public IssueView getSelectedIssueView() {
    return myIssuePanel.getSelectedIssueView();
  }

  public String getTitle() {
    return myIssuePanel.getTitleText();
  }
}
