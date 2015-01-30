/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GradleExperimentalSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  @NotNull private final GradleExperimentalSettings mySettings;

  private JPanel myPanel;
  private JCheckBox myEnableModuleSelectionOnImportCheckBox;
  private JCheckBox myEnableUnitTestingSupportCheckBox;

  public GradleExperimentalSettingsConfigurable() {
    mySettings = GradleExperimentalSettings.getInstance();
  }

  @Override
  @NotNull
  public String getId() {
    return "gradle.experimental";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Experimental";
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return mySettings.SELECT_MODULES_ON_PROJECT_IMPORT != isModuleSelectionOnImportEnabled() ||
           mySettings.ENABLE_UNIT_TESTING_SUPPORT != isUnitTestingEnabled();
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.SELECT_MODULES_ON_PROJECT_IMPORT = isModuleSelectionOnImportEnabled();
    mySettings.setUnitTestingSupportEnabled(isUnitTestingEnabled());
  }

  private boolean isModuleSelectionOnImportEnabled() {
    return myEnableModuleSelectionOnImportCheckBox.isSelected();
  }

  private boolean isUnitTestingEnabled() {
    return myEnableUnitTestingSupportCheckBox.isSelected();
  }

  @Override
  public void reset() {
    myEnableModuleSelectionOnImportCheckBox.setSelected(mySettings.SELECT_MODULES_ON_PROJECT_IMPORT);
    myEnableUnitTestingSupportCheckBox.setSelected(mySettings.ENABLE_UNIT_TESTING_SUPPORT);
  }

  @Override
  public void disposeUIResources() {
  }
}
