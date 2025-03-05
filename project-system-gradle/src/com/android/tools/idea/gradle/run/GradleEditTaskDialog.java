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
}
