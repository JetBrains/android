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
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.UpdateResult;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.fd.InstantRunManager.MINIMUM_GRADLE_PLUGIN_VERSION;
import static com.android.tools.idea.fd.InstantRunManager.MINIMUM_GRADLE_PLUGIN_VERSION_STRING;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

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

  private JBCheckBox myEnableRecorder;
  private HyperlinkLabel myExtraInfoHyperlink;
  private HyperlinkLabel myPrivacyPolicyLink;

  private HyperlinkLabel myReenableLink;
  private JPanel myHelpGooglePanel;
  private JBLabel myHavingTroubleLabel;

  public InstantRunConfigurable() {
    myExtraInfoHyperlink.setHtmlText("Learn more about <a href=\"more\">what is logged</a>,");
    myExtraInfoHyperlink.addHyperlinkListener(e -> BrowserUtil.browse("https://developer.android.com/r/studio-ui/ir-flight-recorder.html"));

    myPrivacyPolicyLink.setHtmlText("and our <a href=\"privacy\">privacy policy.</a>");
    myPrivacyPolicyLink.addHyperlinkListener(e -> BrowserUtil.browse("https://www.google.com/policies/privacy/"));

    myHelpGooglePanel.setBackground(UIUtil.getPanelBackground().brighter());
    myHelpGooglePanel.setBorder(BorderFactory.createLineBorder(JBColor.GRAY));
    myHavingTroubleLabel.setFont(JBUI.Fonts.label().asBold().biggerOn(1.2f));

    myReenableLink.setHtmlText("<a href=\"reenable\">Re-enable and activate extra logging</a>");
    myReenableLink.addHyperlinkListener(e -> {
      myInstantRunCheckBox.setSelected(true);
      enableIrOptions(true);
      myEnableRecorder.setSelected(true);
    });

    myInstantRunCheckBox.addActionListener(e -> enableIrOptions(myInstantRunCheckBox.isSelected()));

    myBuildConfiguration = InstantRunConfiguration.getInstance();
    updateLinkState();
    enableIrOptions(myBuildConfiguration.INSTANT_RUN);
  }

  @NotNull
  @Override
  public String getId() {
    return "instant.run";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Instant Run";
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
           myBuildConfiguration.SHOW_IR_STATUS_NOTIFICATIONS != isShowStatusNotifications() ||
           myBuildConfiguration.ENABLE_RECORDER != isEnableRecorder();
  }

  @Override
  public void apply() throws ConfigurationException {
    myBuildConfiguration.INSTANT_RUN = isInstantRunEnabled();
    myBuildConfiguration.RESTART_ACTIVITY = isRestartActivity();
    myBuildConfiguration.SHOW_TOAST = isShowToast();
    myBuildConfiguration.SHOW_IR_STATUS_NOTIFICATIONS = isShowStatusNotifications();
    myBuildConfiguration.ENABLE_RECORDER = isEnableRecorder();
  }

  @Override
  public void reset() {
    myInstantRunCheckBox.setSelected(myBuildConfiguration.INSTANT_RUN);
    myRestartActivityCheckBox.setSelected(myBuildConfiguration.RESTART_ACTIVITY);
    myShowToastCheckBox.setSelected(myBuildConfiguration.SHOW_TOAST);
    myShowIrStatusNotifications.setSelected(myBuildConfiguration.SHOW_IR_STATUS_NOTIFICATIONS);
    myEnableRecorder.setSelected(myBuildConfiguration.ENABLE_RECORDER);
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

  private boolean isEnableRecorder() {
    return myEnableRecorder.isSelected();
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
        AndroidModuleModel model = AndroidModuleModel.get(module);
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
    enableIrOptions(enabled);
  }

  private void enableIrOptions(boolean enabled) {
    myRestartActivityCheckBox.setEnabled(enabled);
    myShowToastCheckBox.setEnabled(enabled);
    myShowIrStatusNotifications.setEnabled(enabled);
    myEnableRecorder.setEnabled(enabled);
    myExtraInfoHyperlink.setEnabled(enabled);

    myHelpGooglePanel.setVisible(!enabled);
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
    AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(project);
    UpdateResult result = updater.updatePluginVersion(GradleVersion.parse(pluginVersion), GradleVersion.parse(GRADLE_LATEST_VERSION));
    if (result.isPluginVersionUpdated() && result.versionUpdateSuccess()) {
      // Should be at least 23.0.2
      String buildToolsVersion = "23.0.2";
      AndroidSdkHandler sdk = AndroidSdks.getInstance().tryToChooseSdkHandler();
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
      GradleWrapper gradleWrapper = GradleWrapper.find(project);
      if (gradleWrapper != null) {
        gradleWrapper.updateDistributionUrlAndDisplayFailure(GRADLE_LATEST_VERSION);
      }

      // Request a sync
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request().setRunInBackground(false).setTrigger(
        TRIGGER_PROJECT_MODIFIED);
      GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener);
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
  public void setupStarted(@NotNull Project project) {
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
    UIUtil.invokeLaterIfNeeded(() -> {
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
    });
  }
}
