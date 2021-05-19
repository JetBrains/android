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

import com.android.tools.idea.common.error.IssuePanel;
import com.android.tools.idea.common.error.IssueView;
import com.android.tools.idea.tests.gui.framework.fixture.JTextComponentWithHtmlFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.error.RenderIssueProvider;
import com.intellij.lang.annotation.HighlightSeverity;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JLabelFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixture for {@link IssuePanel}
 */
public class IssuePanelFixture extends JPanelFixture {

  private final IssuePanel myIssuePanel;

  public IssuePanelFixture(@NotNull Robot robot, @NotNull IssuePanel panel) {
    super(robot, panel);
    myIssuePanel = panel;
  }

  @NotNull
  public JLabelFixture findIssueWithTitle(String issueTitle) throws NullPointerException {
    return label(Matchers.byText(JLabel.class, issueTitle));
  }

  @NotNull
  public JTextComponentWithHtmlFixture findIssueWithContent(@NotNull String content) {
    return JTextComponentWithHtmlFixture.create(robot(), robot().finder().findByType(target(), JTextPane.class));
  }

  @NotNull
  public IssuePanelFixture clickOnLink(@NotNull String content) throws BadLocationException {
    findIssueWithContent(content).clickOnLink(content);
    return this;
  }

  @NotNull
  public IssuePanelFixture clickFixButton() {
    button(Matchers.byText(JButton.class, "Fix")).click();
    return this;
  }

  public boolean hasRenderError() {
    return myIssuePanel.getIssueModel().getIssues()
             .stream()
             .anyMatch(issue -> issue instanceof RenderIssueProvider.NlRenderIssueWrapper && issue.getSeverity() == HighlightSeverity.ERROR)
           && myIssuePanel.getTitleText().matches(".*[Ee]rror.*");
  }

  public boolean containsText(@NotNull String text) {
    return myIssuePanel.containsErrorWithText(text);
  }

  public String getFullIssueText() {
    return myIssuePanel.getFullIssueText();
  }

  @Nullable
  public IssueView getSelectedIssueView() {
    return myIssuePanel.getSelectedIssueView();
  }

  @NotNull
  public String getTitle() {
    return myIssuePanel.getTitleText();
  }
}
