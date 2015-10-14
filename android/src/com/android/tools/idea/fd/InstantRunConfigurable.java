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
package com.android.tools.idea.fd;

import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class InstantRunConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final AndroidGradleBuildConfiguration myBuildConfiguration;
  private JPanel myContentPanel;
  private JCheckBox myInstantRunCheckBox;
  private JCheckBox myRestartActivityCheckBox;
  private JCheckBox myCrashHandlerCheckBox;

  public InstantRunConfigurable(@NotNull Project project) {
    myBuildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
  }

  @NotNull
  @Override
  public String getId() {
    return "instant.run";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Instant Run";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    return myBuildConfiguration.INSTANT_RUN != isInstantRunEnabled() ||
           myBuildConfiguration.RESTART_ACTIVITY != isRestartActivity() ||
           myBuildConfiguration.CRASH_HANDLER != isCrashHandlerEnabled();

  }

  @Override
  public void apply() throws ConfigurationException {
    myBuildConfiguration.INSTANT_RUN = isInstantRunEnabled();
    myBuildConfiguration.RESTART_ACTIVITY = isRestartActivity();
    myBuildConfiguration.CRASH_HANDLER = isCrashHandlerEnabled();
  }

  @Override
  public void reset() {
    myInstantRunCheckBox.setSelected(myBuildConfiguration.INSTANT_RUN);
    myRestartActivityCheckBox.setSelected(myBuildConfiguration.RESTART_ACTIVITY);
    myCrashHandlerCheckBox.setSelected(myBuildConfiguration.CRASH_HANDLER);
  }

  @Override
  public void disposeUIResources() {
  }

  private boolean isInstantRunEnabled() {
    return myInstantRunCheckBox.isSelected();
  }

  private boolean isRestartActivity() {
    return myRestartActivityCheckBox.isSelected();
  }

  private boolean isCrashHandlerEnabled() {
    return myCrashHandlerCheckBox.isSelected();
  }
}
