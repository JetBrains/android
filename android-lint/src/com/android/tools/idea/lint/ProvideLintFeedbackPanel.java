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
import com.google.wireless.android.sdk.stats.LintAction.LintFeedback;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    setupUI();
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

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(7, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Provide feedback on this issue reported by Android Lint:");
    myPanel.add(jBLabel1,
                new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myFalsePositiveButton = new JButton();
    myFalsePositiveButton.setText("False Positive");
    myPanel.add(myFalsePositiveButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Lint incorrectly diagnosed this problem; it's not a real issue.");
    myPanel.add(jBLabel2,
                new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED,
                                    null, null, null, 0, false));
    myGreatButton = new JButton();
    myGreatButton.setText("Great!");
    myPanel.add(myGreatButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel3 = new JBLabel();
    jBLabel3.setText("Lint found a real issue.");
    myPanel.add(jBLabel3,
                new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED,
                                    null, null, null, 0, false));
    myUnclearMessageButton = new JButton();
    myUnclearMessageButton.setText("Unclear Message");
    myPanel.add(myUnclearMessageButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel4 = new JBLabel();
    jBLabel4.setText("The error message and/or full explanation is not clear; I'm not sure what to do.");
    myPanel.add(jBLabel4, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, 1,
                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myOtherButton = new JButton();
    myOtherButton.setText("Other");
    myPanel.add(myOtherButton, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel5 = new JBLabel();
    jBLabel5.setText("There is some other problem with this lint result.");
    myPanel.add(jBLabel5,
                new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED,
                                    null, null, null, 0, false));
    myFeedbackCheckBox = new JBCheckBox();
    myFeedbackCheckBox.setSelected(true);
    myFeedbackCheckBox.setText("Continue to ask for feedback on lint checks");
    myPanel.add(myFeedbackCheckBox, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(-1, 12), new Dimension(-1, 12), null, 0,
                                             false));
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
