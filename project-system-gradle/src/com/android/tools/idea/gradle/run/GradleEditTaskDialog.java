/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleEditTaskDialog extends DialogWrapper {
  private JPanel myContentPanel;
  private JPanel myTaskPanel;
  private TextFieldWithAutoCompletion myTaskField;
  private List<String> myAvailableTasks = new ArrayList<>();

  protected GradleEditTaskDialog(@Nullable Project project) {
    super(project);
    setupUI();

    setTitle("Select Gradle Task");
    setModal(true);

    TextFieldWithAutoCompletionListProvider<String> provider = new TextFieldWithAutoCompletion.StringsCompletionProvider(null, null) {
      @NotNull
      @Override
      public Collection<String> getItems(String prefix, boolean cached, CompletionParameters parameters) {
        setItems(myAvailableTasks);
        return super.getItems(prefix, cached, parameters);
      }
    };
    myTaskField = new TextFieldWithAutoCompletion<String>(project, provider, true, null);
    myTaskPanel.add(myTaskField);

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  public String getGoal() {
    return myTaskField.getText();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTaskField;
  }

  public void setGoal(@Nullable String goal) {
    myTaskField.setText(goal);
  }

  public void setAvailableGoals(List<String> availableTasks) {
    myAvailableTasks = availableTasks;
  }

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Task:");
    myContentPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myTaskPanel = new JPanel();
    myTaskPanel.setLayout(new BorderLayout(0, 0));
    myContentPanel.add(myTaskPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        new Dimension(300, -1), null, null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Leave empty to assemble the relevant module.");
    myContentPanel.add(jBLabel2, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
  }
}
