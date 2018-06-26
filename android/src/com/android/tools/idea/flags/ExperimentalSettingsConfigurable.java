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
package com.android.tools.idea.flags;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.GradlePerProjectExperimentalSettings;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.text.NumberFormatter;

public class ExperimentalSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  @NotNull private final GradleExperimentalSettings mySettings;
  @NotNull private final GradlePerProjectExperimentalSettings myPerProjectSettings;

  private JPanel myPanel;
  private JSpinner myModuleNumberSpinner;
  private JCheckBox mySkipSourceGenOnSyncCheckbox;
  private JCheckBox myUseL2DependenciesCheckBox;
  private JCheckBox myUseSingleVariantSyncCheckbox;
  private JCheckBox myEnableNavEditorCheckbox;

  @SuppressWarnings("unused") // called by IDE
  public ExperimentalSettingsConfigurable(@NotNull Project project) {
    this(GradleExperimentalSettings.getInstance(), GradlePerProjectExperimentalSettings.getInstance(project));
  }

  @VisibleForTesting
  ExperimentalSettingsConfigurable(@NotNull GradleExperimentalSettings settings,
                                   @NotNull GradlePerProjectExperimentalSettings perProjectSettings) {
    mySettings = settings;
    myPerProjectSettings = perProjectSettings;
    // TODO make visible once Gradle Sync switches to L2 dependencies
    myUseL2DependenciesCheckBox.setVisible(false);
    reset();
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
    if (mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC != isSkipSourceGenOnSync() ||
        mySettings.USE_L2_DEPENDENCIES_ON_SYNC != isUseL2DependenciesInSync() ||
        StudioFlags.ENABLE_NAV_EDITOR.get() != enableNavEditor() ||
        myPerProjectSettings.USE_SINGLE_VARIANT_SYNC != isUseSingleVariantSync()) {
      return true;
    }
    Integer value = getMaxModuleCountForSourceGen();
    return value != null && mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN != value;
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = isSkipSourceGenOnSync();
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = isUseL2DependenciesInSync();
    myPerProjectSettings.USE_SINGLE_VARIANT_SYNC = isUseSingleVariantSync();

    Integer value = getMaxModuleCountForSourceGen();
    if (value != null) {
      mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = value;
    }

    StudioFlags.ENABLE_NAV_EDITOR.override(enableNavEditor());
  }

  @VisibleForTesting
  @Nullable
  Integer getMaxModuleCountForSourceGen() {
    Object value = myModuleNumberSpinner.getValue();
    return value instanceof Integer ? (Integer)value : null;
  }

  @TestOnly
  void setMaxModuleCountForSourceGen(int value) {
    myModuleNumberSpinner.setValue(value);
  }

  @VisibleForTesting
  boolean isSkipSourceGenOnSync() {
    return mySkipSourceGenOnSyncCheckbox.isSelected();
  }

  @TestOnly
  void setSkipSourceGenOnSync(boolean value) {
    mySkipSourceGenOnSyncCheckbox.setSelected(value);
  }

  @VisibleForTesting
  boolean isUseL2DependenciesInSync() {
    return myUseL2DependenciesCheckBox.isSelected();
  }

  @TestOnly
  void setUseL2DependenciesInSync(boolean value) {
    myUseL2DependenciesCheckBox.setSelected(value);
  }

  boolean isUseSingleVariantSync() {
    return myUseSingleVariantSyncCheckbox.isSelected();
  }

  @TestOnly
  void setUseSingleVariantSync(boolean value) {
    myUseSingleVariantSyncCheckbox.setSelected(value);
  }

  private boolean enableNavEditor() {
    return myEnableNavEditorCheckbox.isSelected();
  }

  @Override
  public void reset() {
    mySkipSourceGenOnSyncCheckbox.setSelected(mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC);
    myModuleNumberSpinner.setValue(mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN);
    myUseL2DependenciesCheckBox.setSelected(mySettings.USE_L2_DEPENDENCIES_ON_SYNC);
    myUseSingleVariantSyncCheckbox.setSelected(myPerProjectSettings.USE_SINGLE_VARIANT_SYNC);
    myEnableNavEditorCheckbox.setSelected(StudioFlags.ENABLE_NAV_EDITOR.get());
  }

  private void createUIComponents() {
    int value = GradleExperimentalSettings.getInstance().MAX_MODULE_COUNT_FOR_SOURCE_GEN;
    myModuleNumberSpinner = new JSpinner(new SpinnerNumberModel(value, 0, Integer.MAX_VALUE, 1));
    // Force the spinner to accept numbers only.
    JComponent editor = myModuleNumberSpinner.getEditor();
    if (editor instanceof JSpinner.NumberEditor) {
      JFormattedTextField textField = ((JSpinner.NumberEditor)editor).getTextField();
      JFormattedTextField.AbstractFormatter formatter = textField.getFormatter();
      if (formatter instanceof NumberFormatter) {
        ((NumberFormatter)formatter).setAllowsInvalid(false);
      }
    }
  }
}
