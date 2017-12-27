/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

/**
 * The configuration panel for the Android profiler settings.
 */
public class AndroidProfilersPanel implements HyperlinkListener, GradleSyncListener {

  private static final String MINIMUM_GRADLE_PLUGIN_VERSION_STRING = "2.4.0";
  private static final GradleVersion MINIMUM_GRADLE_PLUGIN_VERSION = GradleVersion.parse(MINIMUM_GRADLE_PLUGIN_VERSION_STRING);

  private final Project myProject;
  private JPanel myDescription;
  private JCheckBox myAdvancedProfilingCheckBox;
  private HyperlinkLabel myHyperlinkLabel;
  private JTextPane myAdvancedProfilingDescription;
  private JLabel mySyncStatusMessage;

  public JComponent getComponent() {
    return myDescription;
  }

  AndroidProfilersPanel(Project project, ProfilerState state) {
    myProject = project;
    updateHyperlink("");
    resetFrom(state);
  }

  /**
   * Resets the settings panel to the values in the specified {@link ProfilerState}.
   */
  void resetFrom(ProfilerState state) {
    boolean enabled = myAdvancedProfilingCheckBox.isEnabled();
    myAdvancedProfilingDescription.setBackground(myDescription.getBackground());
    myAdvancedProfilingCheckBox.setSelected(enabled && state.ADVANCED_PROFILING_ENABLED);
  }

  /**
   * Assigns the current UI state to the specified {@link ProfilerState}.
   */
  void applyTo(ProfilerState state) {
    state.ADVANCED_PROFILING_ENABLED = StudioFlags.PROFILER_ENABLED.get() && myAdvancedProfilingCheckBox.isSelected();
  }

  private void createUIComponents() {
    // TODO: Hyperlink label has a fixed 2 pixel offset at the beginning and cannot be indented. Change for a good component later.
    myHyperlinkLabel = new HyperlinkLabel();
    myHyperlinkLabel.addHyperlinkListener(this);
  }

  private void updateHyperlink(String message) {
    boolean supported = true;
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidModuleModel model = AndroidModuleModel.get(module);
      if (model != null) {
        GradleVersion modelVersion = model.getModelVersion();
        supported = modelVersion != null && modelVersion.compareIgnoringQualifiers(MINIMUM_GRADLE_PLUGIN_VERSION) >= 0;
        if (!supported) {
          break;
        }
      }
    }
    myHyperlinkLabel.setHyperlinkText("This feature can only be enabled with a gradle plugin version of 2.4 or greater. ","Update project", "");
    myHyperlinkLabel.setVisible(!supported);
    mySyncStatusMessage.setText(message);
    myHyperlinkLabel.setVisible(!supported);
    myDescription.setEnabled(supported);
    myAdvancedProfilingCheckBox.setEnabled(supported);
  }

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
    GradleVersion gradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
    GradleVersion pluginVersion = GradleVersion.parse(AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion());

    // Don't bother sync'ing if latest is less than our minimum requirement.
    if (MINIMUM_GRADLE_PLUGIN_VERSION.compareIgnoringQualifiers(pluginVersion) > 0) {
      updateHyperlink("(No matching gradle version found)");
      return;
    }

    // Update plugin version
    AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(myProject);
    AndroidPluginVersionUpdater.UpdateResult result = updater.updatePluginVersion(pluginVersion, gradleVersion);
    if (result.isPluginVersionUpdated() && result.versionUpdateSuccess()) {
      // Request a sync
      // TODO change to plugin upgrade trigger if it is created
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request().setRunInBackground(false).setTrigger(
        TRIGGER_PROJECT_MODIFIED);
      GradleSyncInvoker.getInstance().requestProjectSync(myProject, request, this);
    } else {
      updateHyperlink("(Update failed)");
    }
  }

  @Override
  public void syncStarted(@NotNull Project project) {
    updateHyperlink("(Syncing...)");
  }

  @Override
  public void setupStarted(@NotNull Project project) {
  }

  @Override
  public void syncSucceeded(@NotNull Project project) {
    updateHyperlink("");
  }

  @Override
  public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
    updateHyperlink("(Sync failed)");
  }

  @Override
  public void syncSkipped(@NotNull Project project) {
    updateHyperlink("");
  }
}
