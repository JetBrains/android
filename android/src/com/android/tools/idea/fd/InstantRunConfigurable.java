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

import com.android.ide.common.repository.GradleVersion;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.File;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.fd.InstantRunManager.MINIMUM_GRADLE_PLUGIN_VERSION;
import static com.android.tools.idea.fd.InstantRunManager.MINIMUM_GRADLE_PLUGIN_VERSION_STRING;

public class InstantRunConfigurable
    implements SearchableConfigurable, Configurable.NoScroll, HyperlinkListener, GradleSyncListener, Disposable {
  private final InstantRunConfiguration myBuildConfiguration;
  private JPanel myContentPanel;
  private JBCheckBox myInstantRunCheckBox;
  private JBCheckBox myRestartActivityCheckBox;
  private JBLabel myGradleLabel;
  private HyperlinkLabel myOldVersionLabel;
  private JBCheckBox myShowToastCheckBox;
  private JBCheckBox myShowIrStatusNotifications;

  public InstantRunConfigurable() {
    myBuildConfiguration = InstantRunConfiguration.getInstance();
    updateLinkState();
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
           myBuildConfiguration.SHOW_TOAST != isShowToast() ||
           myBuildConfiguration.SHOW_IR_STATUS_NOTIFICATIONS != isShowStatusNotifications();
  }

  @Override
  public void apply() throws ConfigurationException {
    myBuildConfiguration.INSTANT_RUN = isInstantRunEnabled();
    myBuildConfiguration.RESTART_ACTIVITY = isRestartActivity();
    myBuildConfiguration.SHOW_TOAST = isShowToast();
    myBuildConfiguration.SHOW_IR_STATUS_NOTIFICATIONS = isShowStatusNotifications();

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project.isDefault()) {
        continue;
      }
      InstantRunManager.updateFileListener(project);
    }
  }

  @Override
  public void reset() {
    myInstantRunCheckBox.setSelected(myBuildConfiguration.INSTANT_RUN);
    myRestartActivityCheckBox.setSelected(myBuildConfiguration.RESTART_ACTIVITY);
    myShowToastCheckBox.setSelected(myBuildConfiguration.SHOW_TOAST);
    myShowIrStatusNotifications.setSelected(myBuildConfiguration.SHOW_IR_STATUS_NOTIFICATIONS);
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

  private boolean isShowToast() {
    return myShowToastCheckBox.isSelected();
  }

  private boolean isShowStatusNotifications() {
    return myShowIrStatusNotifications.isSelected();
  }

  private void createUIComponents() {
    myOldVersionLabel = new HyperlinkLabel();
    setSyncLinkMessage("");
    myOldVersionLabel.addHyperlinkListener(this);
  }

  private void setSyncLinkMessage(@NotNull String syncMessage) {
    myOldVersionLabel.setHyperlinkText("Instant Run requires a newer version of the Gradle plugin. ", "Update Project", syncMessage);
    myOldVersionLabel.repaint();
  }

  private void updateLinkState() {
    boolean isGradle = false;
    boolean isCurrentPlugin = false;

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project.isDefault()) {
        continue;
      }
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        AndroidGradleModel model = AndroidGradleModel.get(module);
        if (model != null) {
          isGradle = true;
          if (InstantRunGradleUtils.modelSupportsInstantRun(model)) {
            isCurrentPlugin = true;
            break;
          }
        }
      }
    }

    myGradleLabel.setVisible(!isGradle);
    myOldVersionLabel.setVisible(isGradle && !isCurrentPlugin);

    boolean enabled = isGradle && isCurrentPlugin;

    myInstantRunCheckBox.setEnabled(isGradle); // allow turning off instant run even if the plugin is not the latest
    myRestartActivityCheckBox.setEnabled(enabled);
    myShowToastCheckBox.setEnabled(enabled);
    myShowIrStatusNotifications.setEnabled(enabled);
  }

  @Override
  public void hyperlinkUpdate(HyperlinkEvent hyperlinkEvent) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project.isDefault()) {
        continue;
      }
      if (!updateProjectToInstantRunTools(project, this)) {
        setSyncLinkMessage("Error updating to new Gradle version");
      }
    }
  }

  /** Update versions relevant for Instant Run, and trigger a Gradle sync if successful */
  public static boolean updateProjectToInstantRunTools(@NotNull Project project, @Nullable GradleSyncListener listener) {
    String pluginVersion = MINIMUM_GRADLE_PLUGIN_VERSION_STRING;
    // Pick max version of "recommended Gradle plugin" and "minimum required for instant run"
    if (GradleVersion.parse(GRADLE_PLUGIN_RECOMMENDED_VERSION).compareTo(MINIMUM_GRADLE_PLUGIN_VERSION) > 0) {
      pluginVersion = GRADLE_PLUGIN_RECOMMENDED_VERSION;
    }

    // Update plugin version
    if (GradleUtil.updateGradlePluginVersion(project, pluginVersion, GRADLE_LATEST_VERSION)) {
      // Should be at least 23.0.2
      String buildToolsVersion = "23.0.2";
      AndroidSdkHandler sdk = AndroidSdkUtils.tryToChooseSdkHandler();
      BuildToolInfo latestBuildTool = sdk.getLatestBuildTool(new StudioLoggerProgressIndicator(InstantRunConfigurable.class), false);
      if (latestBuildTool != null) {
        Revision revision = latestBuildTool.getRevision();
        if (revision.compareTo(Revision.parseRevision(buildToolsVersion)) > 0) {
          buildToolsVersion = revision.toShortString();
        }
      }

      // Also update build files to set build tools version 23.0.2
      GradleUtil.setBuildToolsVersion(project, buildToolsVersion);

      // Also update Gradle wrapper version
      File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);
      if (wrapperPropertiesFile != null) {
        GradleUtil.updateGradleDistributionUrl(project, wrapperPropertiesFile, GRADLE_LATEST_VERSION);
      }

      // Request a sync
      GradleProjectImporter.getInstance().syncProjectSynchronously(project, true, listener);
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public void dispose() {
  }

  // ---- Implements GradleSyncListener ----

  @Override
  public void syncStarted(@NotNull Project project) {
    updateUi(true, false);
  }

  @Override
  public void syncSucceeded(@NotNull Project project) {
    updateUi(false, false);
  }

  @Override
  public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
    updateUi(false, true);
  }

  @Override
  public void syncSkipped(@NotNull Project project) {
    updateUi(false, false);
  }

  private void updateUi(final boolean syncing, final boolean failed) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (myContentPanel.isShowing()) {
          if (syncing) {
            setSyncLinkMessage("(Syncing)");
          } else if (failed) {
            setSyncLinkMessage("(Sync Failed)");
          } else {
            setSyncLinkMessage("");
          }
          updateLinkState();
        }
      }
    });
  }
}
