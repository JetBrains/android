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


  public String getFullIssueText() {
    return myIssuePanel.getFullIssueText();
  }

  @NotNull
  public String getTitle() {
    return myIssuePanel.getTitleText();
  }
}
