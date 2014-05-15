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
package com.android.tools.idea.wizard;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * A step that lets you select a Github repository to clone.
 * TODO: Save previously used URLs
 * TODO: Allow selection of branches
 */
public class ChooseGithubRepositoryStep extends TemplateWizardStep {
  private JTextField myUrlField;
  private JBLabel myDescription;
  private JPanel myPanel;
  public static final String GITHUB_TEMPLATE_KEY = "githubUrl";
  private String myBranch = null;

  public ChooseGithubRepositoryStep(@NotNull TemplateWizardState state,
                                    @Nullable Project project,
                                    @Nullable Module module,
                                    UpdateListener updateListener) {
    super(state, project, module, null, updateListener);
    register(GITHUB_TEMPLATE_KEY, myUrlField);
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }

    String urlText = myUrlField.getText();
    if (urlText.isEmpty()) {
      setErrorHtml("URL field may not be empty");
      return false;
    }

    URL url;
    try {
      url = new URL(urlText);
    }
    catch (MalformedURLException e) {
      setErrorHtml("Malformed URL");
      return false;
    }
    if (!url.getProtocol().equalsIgnoreCase("https")) {
      setErrorHtml("GitHub URLs must be HTTPS");
      return false;
    }

    if (!url.getHost().equalsIgnoreCase("github.com")) {
      setErrorHtml("That is not a GitHub URL");
      return false;
    }
    return true;
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myUrlField;
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myDescription;
  }

  @NotNull
  public String getUrl() {
    return myUrlField.getText();
  }

  @Nullable
  public String getBranch() {
    // TODO: Implement this properly.
    return myBranch;
  }
}
