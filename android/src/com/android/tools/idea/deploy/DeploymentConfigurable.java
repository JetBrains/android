/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.deploy;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class DeploymentConfigurable implements Configurable, Configurable.NoScroll {
  private final DeploymentConfiguration myConfiguration;
  private JPanel myContentPanel;
  private JBCheckBox myRunAfterApplyChanges;
  private JBCheckBox myRunAfterApplyCodeChanges;

  public DeploymentConfigurable() {
    myConfiguration = DeploymentConfiguration.getInstance();
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("configurable.DeploymentConfigurable.displayName");
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    return myConfiguration.APPLY_CHANGES_FALLBACK_TO_RUN != myRunAfterApplyChanges.isSelected() ||
           myConfiguration.APPLY_CODE_CHANGES_FALLBACK_TO_RUN != myRunAfterApplyCodeChanges.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfiguration.APPLY_CHANGES_FALLBACK_TO_RUN = myRunAfterApplyChanges.isSelected();
    myConfiguration.APPLY_CODE_CHANGES_FALLBACK_TO_RUN = myRunAfterApplyCodeChanges.isSelected();
  }

  @Override
  public void reset() {
    myRunAfterApplyChanges.setSelected(myConfiguration.APPLY_CHANGES_FALLBACK_TO_RUN);
    myRunAfterApplyCodeChanges.setSelected(myConfiguration.APPLY_CODE_CHANGES_FALLBACK_TO_RUN);
  }
}
