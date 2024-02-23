/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.flags.ExperimentalConfigurable;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.options.ConfigurationException;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class GradleExperimentalSettingsConfigurable implements ExperimentalConfigurable {
  private JCheckBox myUseMultiVariantExtraArtifacts;
  private JCheckBox myConfigureAllGradleTasks;
  private JCheckBox myEnableParallelSync;
  private JCheckBox myEnableDeviceApiOptimization;
  private JCheckBox myDeriveRuntimeClasspathsForLibraries;
  private JPanel myPanel;

  @NotNull private final GradleExperimentalSettings mySettings;

  public GradleExperimentalSettingsConfigurable() {
    this(GradleExperimentalSettings.getInstance());
  }

  public GradleExperimentalSettingsConfigurable(@NotNull GradleExperimentalSettings settings) {
    mySettings = settings;

    myEnableParallelSync.setVisible(StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get());
    myDeriveRuntimeClasspathsForLibraries.setVisible(StudioFlags.GRADLE_SKIP_RUNTIME_CLASSPATH_FOR_LIBRARIES.get());
    myEnableDeviceApiOptimization.setVisible(StudioFlags.API_OPTIMIZATION_ENABLE.get());
    myUseMultiVariantExtraArtifacts.setVisible(StudioFlags.GRADLE_MULTI_VARIANT_ADDITIONAL_ARTIFACT_SUPPORT.get());
    reset();
  }

  @Override
  public @Nullable JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS != isUseMultiVariantExtraArtifact() ||
           // SKIP_GRADLE_TASK_LIST is reversed since original text implies the opposite action.
           mySettings.SKIP_GRADLE_TASKS_LIST == isConfigureAllGradleTasksEnabled() ||
           mySettings.ENABLE_PARALLEL_SYNC != isParallelSyncEnabled() ||
           mySettings.ENABLE_GRADLE_API_OPTIMIZATION != isGradleApiOptimizationEnabled() ||
           mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES != isDeriveRuntimeClasspathsForLibraries();
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = isUseMultiVariantExtraArtifact();
    mySettings.SKIP_GRADLE_TASKS_LIST = !isConfigureAllGradleTasksEnabled();
    mySettings.ENABLE_PARALLEL_SYNC = isParallelSyncEnabled();
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = isGradleApiOptimizationEnabled();
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = isDeriveRuntimeClasspathsForLibraries();
  }

  @Override
  public void reset() {
    myUseMultiVariantExtraArtifacts.setSelected(mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS);
    myConfigureAllGradleTasks.setSelected(!mySettings.SKIP_GRADLE_TASKS_LIST);
    myEnableParallelSync.setSelected(mySettings.ENABLE_PARALLEL_SYNC);
    myEnableDeviceApiOptimization.setSelected(mySettings.ENABLE_GRADLE_API_OPTIMIZATION);
    myDeriveRuntimeClasspathsForLibraries.setSelected(mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES);
  }

  @VisibleForTesting
  boolean isUseMultiVariantExtraArtifact() {
    return myUseMultiVariantExtraArtifacts.isSelected();
  }

  @TestOnly
  void enableUseMultiVariantExtraArtifacts(boolean value) {
    myUseMultiVariantExtraArtifacts.setSelected(value);
  }

  boolean isConfigureAllGradleTasksEnabled() {
    return myConfigureAllGradleTasks.isSelected();
  }

  @TestOnly
  void enableConfigureAllGradleTasks(boolean value) {
    myConfigureAllGradleTasks.setSelected(value);
  }

   boolean isParallelSyncEnabled() {
    return myEnableParallelSync.isSelected();
  }

  @TestOnly
  void enableParallelSync(boolean value) {
    myEnableParallelSync.setSelected(value);
  }

  boolean isGradleApiOptimizationEnabled() {
    return myEnableDeviceApiOptimization.isSelected();
  }

  @TestOnly
  void enableGradleApiOptimization(boolean value) {
    myEnableDeviceApiOptimization.setSelected(value);
  }

  boolean isDeriveRuntimeClasspathsForLibraries() {
    return myDeriveRuntimeClasspathsForLibraries.isSelected();
  }

  @TestOnly
  void enableDeriveRuntimeClasspathsForLibraries(boolean value) {
    myDeriveRuntimeClasspathsForLibraries.setSelected(value);
  }

}
