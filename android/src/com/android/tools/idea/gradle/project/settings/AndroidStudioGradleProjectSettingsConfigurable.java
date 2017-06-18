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
package com.android.tools.idea.gradle.project.settings;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;

/**
 * Configuration panel for Android Studio-specific Gradle project settings.
 */
public class AndroidStudioGradleProjectSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  @NotNull private final AndroidStudioGradleProjectSettings mySettings;

  private JPanel myMainPanel;
  private JCheckBox myDisableEmbeddedRepoCheckbox;

  public AndroidStudioGradleProjectSettingsConfigurable(@NotNull Project project) {
    mySettings = AndroidStudioGradleProjectSettings.getInstance(project);
    reset();
  }

  @Override
  @NotNull
  public String getId() {
    return "android.studio.gradle";
  }

  @Override
  @Nullable
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    return isEmbeddedRepoDisabled() != mySettings.DISABLE_EMBEDDED_MAVEN_REPO;
  }

  @Override
  public void apply() {
    mySettings.DISABLE_EMBEDDED_MAVEN_REPO = isEmbeddedRepoDisabled();
  }

  @VisibleForTesting
  boolean isEmbeddedRepoDisabled() {
    return myDisableEmbeddedRepoCheckbox.isSelected();
  }

  @TestOnly
  void disableEmbeddedRepo() {
    myDisableEmbeddedRepoCheckbox.setSelected(true);
  }

  @Override
  public void reset() {
    myDisableEmbeddedRepoCheckbox.setSelected(mySettings.DISABLE_EMBEDDED_MAVEN_REPO);
  }

  @Override
  public String getDisplayName() {
    return "Android Studio";
  }
}
