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

import com.android.tools.idea.concurrent.EdtExecutor;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
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

public class InstantRunConfigurable
    implements SearchableConfigurable, Configurable.NoScroll, HyperlinkListener, Disposable {
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
          if (ProjectSystemUtil.getModuleSystem(module).getInstantRunSupport().isSupported()) {
            isCurrentPlugin = true;
            break;
          }
        }
      }
    }

    myGradleLabel.setVisible(!isGradle);
    myOldVersionLabel.setVisible(isGradle && !isCurrentPlugin);

    boolean enabled = isGradle && isCurrentPlugin;

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

      AndroidProjectSystem projectSystem = ProjectSystemUtil.getProjectSystem(project);
      if (projectSystem.upgradeProjectToSupportInstantRun()) {
        updateUi(true, false);
        Futures.addCallback(projectSystem.getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, false),
                            new FutureCallback<ProjectSystemSyncManager.SyncResult>() {
            @Override
            public void onSuccess(ProjectSystemSyncManager.SyncResult result) {
              updateUi(false, result.isSuccessful());
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
              updateUi(false, false);
            }
          }, EdtExecutor.INSTANCE);
      } else {
        showFailureMessage();
      }
    }
  }

  private void showFailureMessage() {
    setSyncLinkMessage("Error updating to new Gradle version");
  }

  @Override
  public void dispose() {
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
