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

import static com.android.tools.idea.gradle.project.SyncDueMessageKt.SYNC_DUE_DIALOG_SHOWN;
import static com.android.tools.idea.gradle.project.SyncDueMessageKt.SYNC_DUE_SNOOZED_SETTING;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.flags.ExperimentalConfigurable;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.AutoSyncBehavior;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AutoSyncSettingChangeEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class GradleExperimentalSettingsConfigurable implements ExperimentalConfigurable {
  private JCheckBox myUseMultiVariantExtraArtifacts;
  private JCheckBox myConfigureAllGradleTasks;
  private JCheckBox myEnableParallelSync;
  private JCheckBox myEnableDeviceApiOptimization;
  private JCheckBox myDeriveRuntimeClasspathsForLibraries;
  private JCheckBox myShowAgpVersionChooserInNewProjectWizard;
  private JPanel myPanel;
  private JComboBox<AutoSyncBehavior> autoSyncBehaviorComboBox;

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
    myShowAgpVersionChooserInNewProjectWizard.setVisible(StudioFlags.NPW_SHOW_AGP_VERSION_COMBO_BOX_EXPERIMENTAL_SETTING.get());
    autoSyncBehaviorComboBox.setModel(new EnumComboBoxModel<>(AutoSyncBehavior.class));
    autoSyncBehaviorComboBox.setRenderer(
      SimpleListCellRenderer.create("", behavior -> AndroidBundle.message(behavior.getLabelBundleKey())));
    autoSyncBehaviorComboBox.getParent().setVisible(StudioFlags.SHOW_GRADLE_AUTO_SYNC_SETTING_UI.get());
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
           mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES != isDeriveRuntimeClasspathsForLibraries() ||
           mySettings.SHOW_ANDROID_GRADLE_PLUGIN_VERSION_COMBO_BOX_IN_NEW_PROJECT_WIZARD != isShowAgpVersionChooserInNewProjectWizard() ||
           mySettings.AUTO_SYNC_BEHAVIOR != getAutoSyncBehaviorComboBox();
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = isUseMultiVariantExtraArtifact();
    mySettings.SKIP_GRADLE_TASKS_LIST = !isConfigureAllGradleTasksEnabled();
    mySettings.ENABLE_PARALLEL_SYNC = isParallelSyncEnabled();
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = isGradleApiOptimizationEnabled();
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = isDeriveRuntimeClasspathsForLibraries();
    mySettings.SHOW_ANDROID_GRADLE_PLUGIN_VERSION_COMBO_BOX_IN_NEW_PROJECT_WIZARD = isShowAgpVersionChooserInNewProjectWizard();
    if (mySettings.AUTO_SYNC_BEHAVIOR != getAutoSyncBehaviorComboBox()) {
      mySettings.AUTO_SYNC_BEHAVIOR = getAutoSyncBehaviorComboBox();
      trackAutoSyncSettingChanged();
      clearAutoSyncVariables();
    }
  }

  @Override
  public void reset() {
    myUseMultiVariantExtraArtifacts.setSelected(mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS);
    myConfigureAllGradleTasks.setSelected(!mySettings.SKIP_GRADLE_TASKS_LIST);
    myEnableParallelSync.setSelected(mySettings.ENABLE_PARALLEL_SYNC);
    myEnableDeviceApiOptimization.setSelected(mySettings.ENABLE_GRADLE_API_OPTIMIZATION);
    myDeriveRuntimeClasspathsForLibraries.setSelected(mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES);
    myShowAgpVersionChooserInNewProjectWizard.setSelected(mySettings.SHOW_ANDROID_GRADLE_PLUGIN_VERSION_COMBO_BOX_IN_NEW_PROJECT_WIZARD);
    autoSyncBehaviorComboBox.setSelectedIndex(AutoSyncBehavior.getEntries().indexOf(GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR));
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

  public boolean isShowAgpVersionChooserInNewProjectWizard() {
    return myShowAgpVersionChooserInNewProjectWizard.isSelected();
  }

  @TestOnly
  public void enableShowAndroidGradlePluginVersionChooserInNewProjectWizard(boolean value) {
    myShowAgpVersionChooserInNewProjectWizard.setSelected(value);
  }

  AutoSyncBehavior getAutoSyncBehaviorComboBox() {
    return (AutoSyncBehavior)autoSyncBehaviorComboBox.getSelectedItem();
  }

  /**
   * Clears snooze and first dialog flags that are used by Optional Auto Sync feature.
   */
  private void clearAutoSyncVariables() {
    PropertiesComponent.getInstance().unsetValue(SYNC_DUE_SNOOZED_SETTING);
    PropertiesComponent.getInstance().unsetValue(SYNC_DUE_DIALOG_SHOWN);
  }

  private void trackAutoSyncSettingChanged() {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.AUTO_SYNC_SETTING_CHANGE)
        .setAutoSyncSettingChangeEvent(
          AutoSyncSettingChangeEvent.newBuilder()
            .setState(getAutoSyncBehaviorComboBox() == AutoSyncBehavior.Default)
            .setChangeSource(AutoSyncSettingChangeEvent.ChangeSource.SETTINGS)
            .build()
        )
    );
  }
}
