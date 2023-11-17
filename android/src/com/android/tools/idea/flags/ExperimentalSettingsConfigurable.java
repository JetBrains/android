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

import static com.android.tools.idea.flags.ExperimentalSettingsConfigurable.TraceProfileItem.DEFAULT;
import static com.android.tools.idea.flags.ExperimentalSettingsConfigurable.TraceProfileItem.SPECIFIED_LOCATION;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.sync.idea.TraceSyncUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.TitledSeparator;
import java.io.File;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class ExperimentalSettingsConfigurable extends CompositeConfigurable<UnnamedConfigurable> implements SearchableConfigurable {
  @NotNull private final GradleExperimentalSettings mySettings;

  private JPanel myPanel;
  private JCheckBox myUseMultiVariantExtraArtifacts;
  private JCheckBox myConfigureAllGradleTasks;
  private JCheckBox myTraceGradleSyncCheckBox;
  private JComboBox<TraceProfileItem> myTraceProfileComboBox;
  private TextFieldWithBrowseButton myTraceProfilePathField;
  private JCheckBox myEnableParallelSync;

  private JCheckBox myEnableDeviceApiOptimization;
  private JCheckBox myDeriveRuntimeClasspathsForLibraries;
  private JPanel myExtensionPanel;

  private final Project myProject;
  private final SortedMap<String, UnnamedConfigurable> myConfigurableMap;

  private final boolean forAndroidStudio;

  @SuppressWarnings("unused") // called by IDE
  public ExperimentalSettingsConfigurable(@NotNull Project project, boolean studio) {
    this(project, GradleExperimentalSettings.getInstance(), studio);
  }

  @VisibleForTesting
  ExperimentalSettingsConfigurable(@NotNull Project project,
                                   @NotNull GradleExperimentalSettings settings,
                                   boolean studio) {
    myConfigurableMap = new TreeMap<>();
    myProject = project;
    forAndroidStudio = studio;
    mySettings = settings;

    myEnableParallelSync.setVisible(StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get());
    myDeriveRuntimeClasspathsForLibraries.setVisible(StudioFlags.GRADLE_SKIP_RUNTIME_CLASSPATH_FOR_LIBRARIES.get());
    myEnableDeviceApiOptimization.setVisible(StudioFlags.API_OPTIMIZATION_ENABLE.get());

    initTraceComponents();
    reset();
  }

  @Override
  @NotNull
  public String getId() {
    if (forAndroidStudio) {
      return "experimental";
    }
    else {
      return "experimentalPlugin";
    }
  }

  @Override
  @Nls
  public String getDisplayName() {
    if (forAndroidStudio) {
      return "Experimental";
    }
    else {
      return "Android (Experimental)";
    }
  }

  @Override
  @NotNull
  public List<UnnamedConfigurable> createConfigurables() {
    for (ExperimentalSettingsContributor contributor : ExperimentalSettingsContributor.EP_NAME.getExtensions()) {
      if (contributor.shouldCreateConfigurable(myProject)) {
        UnnamedConfigurable configurable = contributor.createConfigurable(myProject);
        String name = contributor.getName();
        myConfigurableMap.put(name, configurable);
      }
    }
    return myConfigurableMap.values().stream().toList();
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    myExtensionPanel.setLayout(new BoxLayout(myExtensionPanel, BoxLayout.PAGE_AXIS));
    myConfigurableMap.forEach((name, configurable) -> {
      myExtensionPanel.add(new TitledSeparator(name));
      myExtensionPanel.add(configurable.createComponent());
    });
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS != isUseMultiVariantExtraArtifact() ||
           // SKIP_GRADLE_TASK_LIST is reversed since original text implies the opposite action.
           mySettings.SKIP_GRADLE_TASKS_LIST == isConfigureAllGradleTasksEnabled() ||
           mySettings.TRACE_GRADLE_SYNC != isTraceGradleSyncEnabled() ||
           mySettings.TRACE_PROFILE_SELECTION != getTraceProfileSelection() ||
           !mySettings.TRACE_PROFILE_LOCATION.equals(getTraceProfileLocation()) ||
           mySettings.ENABLE_PARALLEL_SYNC != isParallelSyncEnabled() ||
           mySettings.ENABLE_GRADLE_API_OPTIMIZATION != isGradleApiOptimizationEnabled() ||
           mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES != isDeriveRuntimeClasspathsForLibraries() ||
           super.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = isUseMultiVariantExtraArtifact();
    mySettings.SKIP_GRADLE_TASKS_LIST = !isConfigureAllGradleTasksEnabled();
    mySettings.ENABLE_PARALLEL_SYNC = isParallelSyncEnabled();
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = isGradleApiOptimizationEnabled();
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = isDeriveRuntimeClasspathsForLibraries();

    applyTraceSettings();
    super.apply();
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
  TraceProfileItem getTraceProfileSelection() {
    return (TraceProfileItem)myTraceProfileComboBox.getSelectedItem();
  }

  @TestOnly
  void setTraceProfileSelection(@NotNull TraceProfileItem value) {
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


  private void updateTraceComponents() {
    myTraceProfileComboBox.setEnabled(myTraceGradleSyncCheckBox.isSelected());
    // Enable text field only if trace is enabled, and using profile from local disk.
    myTraceProfilePathField
      .setEnabled(myTraceGradleSyncCheckBox.isSelected() && SPECIFIED_LOCATION.equals(myTraceProfileComboBox.getSelectedItem()));
    if (isTraceProfileInvalid()) {
      ExternalSystemUiUtil.showBalloon(myTraceProfilePathField, MessageType.WARNING, "Invalid profile location");
    }
  }

  private void applyTraceSettings() {
    // Don't apply changes if trace profile is not valid.
    if (isTraceProfileInvalid()) {
      ExternalSystemUiUtil.showBalloon(myTraceProfilePathField, MessageType.WARNING, "Invalid profile location");
      return;
    }

    // Don't ask for restart in unit test.
    final Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode()) {
      saveTraceSettings();
      return;
    }

    if (mySettings.TRACE_GRADLE_SYNC != isTraceGradleSyncEnabled() ||
        mySettings.TRACE_PROFILE_SELECTION != getTraceProfileSelection() ||
        !mySettings.TRACE_PROFILE_LOCATION.equals(getTraceProfileLocation())) {
      // Restart of studio is required to apply the changes, because the jvm args are specified at startup of studio.
      boolean canRestart = app.isRestartCapable();
      String okText = canRestart ? "Restart" : "Exit";
      String message = "A restart of Android Studio is required to apply changes related to tracing.\n\n" +
                       "Do you want to proceed?";
      int result = Messages.showOkCancelDialog(message, "Restart", okText, "Cancel", Messages.getQuestionIcon());

      if (result == Messages.OK) {
        saveTraceSettings();
        TraceSyncUtil.updateTraceArgsInFile();
        // Suppress "Are you sure you want to exit Android Studio" dialog, and restart if possible.
        app.exit(false, true, true);
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
    super.reset();
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
