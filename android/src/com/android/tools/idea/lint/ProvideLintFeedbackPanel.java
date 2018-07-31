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
package com.android.tools.idea.lint;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.utils.NullLogger;
import com.google.wireless.android.sdk.stats.LintAction.LintFeedback;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

// TODO: Offer feedback on suggested fixes as well
// TODO: Offer to no longer request feedback

public class ProvideLintFeedbackPanel extends DialogWrapper implements ActionListener, ChangeListener {
  private static final String REQUEST_FEEDBACK = "request-lint-feedback";
  private static Boolean requestFeedback = null;
  private final Project myProject;
  private final String myIssue;
  private JButton myFalsePositiveButton;
  private JButton myGreatButton;
  private JButton myUnclearMessageButton;
  private JButton myOtherButton;
  private JPanel myPanel;
  private JBCheckBox myFeedbackCheckBox;

  public ProvideLintFeedbackPanel(@Nullable Project project, @NotNull String issue) {
    super(project);
    myProject = project;
    myIssue = issue;

    setTitle("Provide Feedback");
    init();
    myGreatButton.addActionListener(this);
    myFalsePositiveButton.addActionListener(this);
    myUnclearMessageButton.addActionListener(this);
    myOtherButton.addActionListener(this);
    myFeedbackCheckBox.addChangeListener(this);
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getCancelAction()};
  }

  @Nullable
  @Override
  protected JPanel createCenterPanel() {
    return myPanel;
  }

  @Override
  public void actionPerformed(ActionEvent e) {

    LintFeedback feedback;
    Object source = e.getSource();
    if (source == myGreatButton) {
      feedback = LintFeedback.LOVE_IT;
    }
    else if (source == myFalsePositiveButton) {
      feedback = LintFeedback.FALSE_POSITIVE;
    }
    else if (source == myUnclearMessageButton) {
      feedback = LintFeedback.UNCLEAR_MESSAGE;
    }
    else if (source == myOtherButton) {
      feedback = LintFeedback.UNKNOWN_FEEDBACK;
    }
    else {
      assert false : source;
      return;
    }

    LintIdeAnalytics analytics = new LintIdeAnalytics(myProject);
    analytics.logFeedback(myIssue, feedback);

    close(CANCEL_EXIT_CODE);
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    if (e.getSource() == myFeedbackCheckBox) {
      boolean selected = myFeedbackCheckBox.isSelected();
      if (selected != canRequestFeedback()) {
        setRequestFeedback(selected);
      }
    }
  }

  /**
   * Whether we should request feedback from the user
   */
  public static boolean canRequestFeedback() {
    if (!AnalyticsSettings.getOptedIn()) {
      return false;
    }
    if (requestFeedback == null) {
      requestFeedback = PropertiesComponent.getInstance().getBoolean(REQUEST_FEEDBACK, true);
    }
    return requestFeedback;
  }

  private static void setRequestFeedback(boolean request) {
    requestFeedback = request;
    PropertiesComponent.getInstance().setValue(REQUEST_FEEDBACK, request);
  }
}
