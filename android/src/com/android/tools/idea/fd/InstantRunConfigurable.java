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

import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixGradleModelVersionHyperlink;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.fd.FastDeployManager.MINIMUM_GRADLE_PLUGIN_VERSION;

public class InstantRunConfigurable
    implements SearchableConfigurable, Configurable.NoScroll, HyperlinkListener, GradleSyncListener, Disposable {
  private final AndroidGradleBuildConfiguration myBuildConfiguration;
  private final Project myProject;
  private JPanel myContentPanel;
  private JCheckBox myInstantRunCheckBox;
  private JCheckBox myRestartActivityCheckBox;
  private JCheckBox myCrashHandlerCheckBox;
  private JBLabel myGradleLabel;
  private HyperlinkLabel myOldVersionLabel;
  private MessageBusConnection myConnection;

  public InstantRunConfigurable(@NotNull Project project) {
    myProject = project;
    myBuildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
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

  private void createUIComponents() {
    myOldVersionLabel = new HyperlinkLabel();
    setSyncLinkMessage("");
    myOldVersionLabel.addHyperlinkListener(this);
  }

  private void setSyncLinkMessage(@NotNull  String syncMessage) {
    myOldVersionLabel.setHyperlinkText("Instant Run requires a newer version of the Gradle plugin. ", "Update Project", syncMessage);
  }

  private void updateLinkState() {
    boolean isGradle = false;
    boolean isCurrentPlugin = false;

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidGradleModel model = AndroidGradleModel.get(module);
      if (model != null) {
        isGradle = true;
        String version = model.getAndroidProject().getModelVersion();
        try {
          FullRevision modelVersion = FullRevision.parseRevision(version);
          if (modelVersion.getMajor() > 1 || modelVersion.getMinor() >= 6) {
            isCurrentPlugin = true;
            break;
          }
        } catch (NumberFormatException e) {
          Logger.getInstance(AndroidGradleModel.class).warn("Failed to parse '" + version + "'", e);
        }
      }
    }

    myGradleLabel.setVisible(!isGradle);
    myOldVersionLabel.setVisible(isGradle && !isCurrentPlugin);

    boolean enabled = isGradle && isCurrentPlugin;

    myInstantRunCheckBox.setEnabled(enabled);
    myRestartActivityCheckBox.setEnabled(enabled);
    myCrashHandlerCheckBox.setEnabled(enabled);
  }

  @Override
  public void hyperlinkUpdate(HyperlinkEvent hyperlinkEvent) {
    new FixGradleModelVersionHyperlink(MINIMUM_GRADLE_PLUGIN_VERSION, GRADLE_LATEST_VERSION, false).execute(myProject);

    myConnection = GradleSyncState.subscribe(myProject, this, this);
  }

  @Override
  public void dispose() {
  }

  // ---- Implements GradleSyncListener ----

  @Override
  public void syncStarted(@NotNull Project project) {
    if (myContentPanel.isShowing()) {
      setSyncLinkMessage("(Syncing)");
      updateLinkState();
    }
  }

  @Override
  public void syncSucceeded(@NotNull Project project) {
    syncFinished();
  }

  @Override
  public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
    if (myContentPanel.isShowing()) {
      setSyncLinkMessage("(Sync Failed)");
    }
    syncFinished();
  }

  @Override
  public void syncSkipped(@NotNull Project project) {
    syncFinished();
  }

  private void syncFinished() {
    if (myContentPanel.isShowing()) {
      updateLinkState();
    }
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
    }
  }
}
