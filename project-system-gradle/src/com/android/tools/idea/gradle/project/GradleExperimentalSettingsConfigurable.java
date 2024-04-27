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

import static com.android.tools.idea.gradle.project.GradleExperimentalSettingsConfigurable.TraceProfileItem.DEFAULT;
import static com.android.tools.idea.gradle.project.GradleExperimentalSettingsConfigurable.TraceProfileItem.SPECIFIED_LOCATION;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor;

import com.android.tools.idea.flags.ExperimentalConfigurable;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.idea.TraceSyncUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import java.io.File;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class GradleExperimentalSettingsConfigurable implements ExperimentalConfigurable {
  private JCheckBox myUseMultiVariantExtraArtifacts;
  private JCheckBox myConfigureAllGradleTasks;
  private JCheckBox myTraceGradleSyncCheckBox;
  private JComboBox myTraceProfileComboBox;
  private TextFieldWithBrowseButton myTraceProfilePathField;
  private JCheckBox myEnableParallelSync;
  private JCheckBox myEnableDeviceApiOptimization;
  private JCheckBox myDeriveRuntimeClasspathsForLibraries;
  private JPanel myPanel;

  private boolean shouldRestart = false;

  @NotNull private final GradleExperimentalSettings mySettings;

  public GradleExperimentalSettingsConfigurable() {
    this(GradleExperimentalSettings.getInstance());
  }

  public GradleExperimentalSettingsConfigurable(@NotNull GradleExperimentalSettings settings) {
    mySettings = settings;

    myEnableParallelSync.setVisible(StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get());
    myDeriveRuntimeClasspathsForLibraries.setVisible(StudioFlags.GRADLE_SKIP_RUNTIME_CLASSPATH_FOR_LIBRARIES.get());
    myEnableDeviceApiOptimization.setVisible(StudioFlags.API_OPTIMIZATION_ENABLE.get());
    initTraceComponents();
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
           isTraceSettingsModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = isUseMultiVariantExtraArtifact();
    mySettings.SKIP_GRADLE_TASKS_LIST = !isConfigureAllGradleTasksEnabled();
    mySettings.ENABLE_PARALLEL_SYNC = isParallelSyncEnabled();
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = isGradleApiOptimizationEnabled();
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = isDeriveRuntimeClasspathsForLibraries();
    applyTraceSettings();
  }

  @Override
  public void reset() {
    myUseMultiVariantExtraArtifacts.setSelected(mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS);
    myConfigureAllGradleTasks.setSelected(!mySettings.SKIP_GRADLE_TASKS_LIST);
    myTraceGradleSyncCheckBox.setSelected(mySettings.TRACE_GRADLE_SYNC);
    myTraceProfileComboBox.setSelectedItem(mySettings.TRACE_PROFILE_SELECTION);
    myTraceProfilePathField.setText(mySettings.TRACE_PROFILE_LOCATION);
    myEnableParallelSync.setSelected(mySettings.ENABLE_PARALLEL_SYNC);
    myEnableDeviceApiOptimization.setSelected(mySettings.ENABLE_GRADLE_API_OPTIMIZATION);
    myDeriveRuntimeClasspathsForLibraries.setSelected(mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES);
    updateTraceComponents();
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

  boolean isTraceGradleSyncEnabled() {
    return myTraceGradleSyncCheckBox.isSelected();
  }

  @TestOnly
  void enableTraceGradleSync(boolean value) {
    myTraceGradleSyncCheckBox.setSelected(value);
  }

  @Nullable
  GradleExperimentalSettingsConfigurable.TraceProfileItem getTraceProfileSelection() {
    return (GradleExperimentalSettingsConfigurable.TraceProfileItem)myTraceProfileComboBox.getSelectedItem();
  }

  @TestOnly
  void setTraceProfileSelection(@NotNull GradleExperimentalSettingsConfigurable.TraceProfileItem value) {
    myTraceProfileComboBox.setSelectedItem(value);
  }

  @NotNull
  String getTraceProfileLocation() {
    return myTraceProfilePathField.getText();
  }

  @TestOnly
  void setTraceProfileLocation(@NotNull String value) {
    myTraceProfilePathField.setText(value);
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

  private void initTraceComponents() {
    myTraceGradleSyncCheckBox.addActionListener(e -> updateTraceComponents());

    myTraceProfileComboBox.addActionListener(e -> {
      myTraceProfilePathField.setEnabled(SPECIFIED_LOCATION.equals(myTraceProfileComboBox.getSelectedItem()));
      if (isTraceProfileInvalid()) {
        ExternalSystemUiUtil.showBalloon(myTraceProfilePathField, MessageType.WARNING, "Invalid profile location");
      }
    });

    myUseMultiVariantExtraArtifacts.setVisible(StudioFlags.GRADLE_MULTI_VARIANT_ADDITIONAL_ARTIFACT_SUPPORT.get());

    myTraceProfilePathField.addBrowseFolderListener("Trace Profile", "Please select trace profile",
                                                    null, createSingleFileDescriptor("profile"));
    myTraceProfileComboBox.addItem(DEFAULT);
    myTraceProfileComboBox.addItem(SPECIFIED_LOCATION);
  }

  private boolean isTraceSettingsModified() {
    return mySettings.TRACE_GRADLE_SYNC != isTraceGradleSyncEnabled() ||
           mySettings.TRACE_PROFILE_SELECTION != getTraceProfileSelection() ||
           !mySettings.TRACE_PROFILE_LOCATION.equals(getTraceProfileLocation());
  }

  private void updateTraceComponents() {
    myTraceProfileComboBox.setEnabled(myTraceGradleSyncCheckBox.isSelected());
    // Enable text field only if trace is enabled, and using profile from local disk.
    myTraceProfilePathField
      .setEnabled(myTraceGradleSyncCheckBox.isSelected() && SPECIFIED_LOCATION.equals(myTraceProfileComboBox.getSelectedItem()));
    if (isTraceProfileInvalid()) {
      ExternalSystemUiUtil.showBalloon(myTraceProfilePathField, MessageType.WARNING, "Invalid profile location");
    }
  }

  @Override
  public @NotNull ApplyState preApplyCallback() {
    // Don't apply changes if trace profile is not valid.
    if (isTraceProfileInvalid()) {
      ExternalSystemUiUtil.showBalloon(myTraceProfilePathField, MessageType.WARNING, "Invalid profile location");
      return ApplyState.BLOCK;
    }

    if (isTraceSettingsModified()) {
      return ApplyState.RESTART;
    }

    return ApplyState.OK;
  }

  private void applyTraceSettings() {
    if (isTraceSettingsModified()) {
      saveTraceSettings();
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        TraceSyncUtil.updateTraceArgsInFile();
      }
    }
  }

  private void saveTraceSettings() {
    mySettings.TRACE_GRADLE_SYNC = isTraceGradleSyncEnabled();
    mySettings.TRACE_PROFILE_SELECTION = getTraceProfileSelection();
    mySettings.TRACE_PROFILE_LOCATION = getTraceProfileLocation();
  }

  @VisibleForTesting
  boolean isTraceProfileInvalid() {
    if (isTraceGradleSyncEnabled()) {
      if (getTraceProfileSelection() == SPECIFIED_LOCATION) {
        File selectedFile = new File(getTraceProfileLocation());
        return !selectedFile.isFile() || !selectedFile.getName().endsWith(".profile");
      }
    }
    return false;
  }

  public enum TraceProfileItem {
    DEFAULT("Default profile"),
    SPECIFIED_LOCATION("Specified location");

    private final String displayValue;

    TraceProfileItem(@NotNull String value) {
      displayValue = value;
    }

    @Override
    public String toString() {
      return displayValue;
    }
  }
}
