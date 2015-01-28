/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixGradleVersionInWrapperHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.*;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

final class PreSyncChecks {
  private static final Logger LOG = Logger.getInstance(PreSyncChecks.class);
  private static final String GRADLE_SYNC_MSG_TITLE = "Gradle Sync";
  private static final String PROJECT_SYNCING_ERROR_GROUP = "Project syncing error";

  private PreSyncChecks() {
  }

  @NotNull
  static PreSyncCheckResult canSync(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      // Unlikely to happen because it would mean this is the default project.
      return PreSyncCheckResult.success();
    }

    ProjectSyncMessages syncMessages = ProjectSyncMessages.getInstance(project);
    syncMessages.removeMessages(PROJECT_SYNCING_ERROR_GROUP);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      GradleUtil.attemptToUseEmbeddedGradle(project);
    }

    return PreSyncCheckResult.success();
  }

  @NotNull
  private static PreSyncCheckResult ensureCorrectGradleSettings(@NotNull Project project, @Nullable FullRevision modelVersion) {
    if (modelVersion == null || createWrapperIfNecessary(project)) {
      // Continue with sync and let it fail.
      return PreSyncCheckResult.success();
    }

    GradleProjectSettings gradleSettings = GradleUtil.getGradleProjectSettings(project);
    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);

    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;

    boolean usingWrapper = (distributionType == null || distributionType == DEFAULT_WRAPPED) && wrapperPropertiesFile != null;
    if (usingWrapper) {
      PreSyncCheckResult result = attemptToUpdateGradleVersionInWrapper(wrapperPropertiesFile, modelVersion, project);
      if (!result.isSuccess()) {
        return result;
      }
      if (gradleSettings != null) {
        // Do this just to ensure that the right distribution type is set.
        gradleSettings.setDistributionType(DEFAULT_WRAPPED);
      }
    }
    else if (distributionType == DistributionType.LOCAL) {
      attemptToUseSupportedLocalGradle(modelVersion, gradleSettings, project);
    }
    return PreSyncCheckResult.success();
  }

  // Returns true if wrapper was created or sync should continue immediately after executing this method.
  private static boolean createWrapperIfNecessary(@NotNull Project project) {
    GradleProjectSettings gradleSettings = GradleUtil.getGradleProjectSettings(project);
    if (gradleSettings == null) {
      // Unlikely to happen. When we get to this point we already created GradleProjectSettings.
      return true;
    }

    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);

    if (wrapperPropertiesFile == null) {
      DistributionType distributionType = gradleSettings.getDistributionType();
      boolean createWrapper = false;
      if (distributionType == null) {
        String msg = "Gradle settings for this project are not configured yet.\n\n" +
                     "Would you like the project to use the Gradle wrapper?\n" +
                     "(The wrapper will automatically download the latest supported Gradle version).\n\n" +
                     "Click 'OK' to use the Gradle wrapper, or 'Cancel' to manually set the path of a local Gradle distribution.";
        int answer = Messages.showOkCancelDialog(project, msg, GRADLE_SYNC_MSG_TITLE, Messages.getQuestionIcon());
        createWrapper = answer == Messages.OK;

      }
      else if (distributionType == DEFAULT_WRAPPED) {
        createWrapper = true;
      }

      if (createWrapper) {
        File projectDirPath = new File(project.getBasePath());

        // attempt to delete the whole gradle wrapper folder.
        File gradleDirPath = new File(projectDirPath, SdkConstants.FD_GRADLE);
        if (!FileUtil.delete(gradleDirPath)) {
          // deletion failed. Let sync continue.
          return true;
        }

        try {
          GradleUtil.createGradleWrapper(projectDirPath);
          if (distributionType == null) {
            gradleSettings.setDistributionType(DEFAULT_WRAPPED);
          }
          return true;
        }
        catch (IOException e) {
          LOG.info("Failed to create Gradle wrapper for project '" + project.getName() + "'", e);
        }
      }
      else if (distributionType == null) {
        ChooseGradleHomeDialog dialog = new ChooseGradleHomeDialog();
        if (dialog.showAndGet()) {
          String enteredGradleHomePath = dialog.getEnteredGradleHomePath();
          gradleSettings.setGradleHome(enteredGradleHomePath);
          gradleSettings.setDistributionType(DistributionType.LOCAL);
          return true;
        }
      }
    }

    return false;
  }

  @NotNull
  private static PreSyncCheckResult attemptToUpdateGradleVersionInWrapper(@NotNull final File wrapperPropertiesFile,
                                                                          @NotNull FullRevision modelVersion,
                                                                          @NotNull Project project) {
    FullRevision minimumPluginVersion = FullRevision.parseRevision(GRADLE_PLUGIN_MINIMUM_VERSION);
    if (modelVersion.compareTo(minimumPluginVersion) < 0) {
      // Do not perform this check for plug-in 0.14. It supports many versions of Gradle.
      // Let sync fail if using an unsupported Gradle versions.
      return PreSyncCheckResult.success();
    }

    String gradleVersion = null;
    try {
      gradleVersion = GradleUtil.getGradleWrapperVersion(wrapperPropertiesFile);
    }
    catch (IOException e) {
      LOG.warn("Failed to read file " + wrapperPropertiesFile.getPath());
    }

    if (gradleVersion == null) {
      // There is a wrapper, but the Gradle version could not be read. Continue with sync.
      return PreSyncCheckResult.success();
    }

    FullRevision gradleRevision = null;
    try {
      gradleRevision = FullRevision.parseRevision(gradleVersion);
    }
    catch (NumberFormatException e) {
      // ignored;
    }

    if (gradleRevision != null && !GradleUtil.isSupportedGradleVersion(gradleRevision)) {
      String cause = getMinimumGradleVersionErrorPrefix(modelVersion);
      NotificationHyperlink hyperlink = FixGradleVersionInWrapperHyperlink.createIfProjectUsesGradleWrapper(project);

      ProjectSyncMessages syncMessages = ProjectSyncMessages.getInstance(project);
      syncMessages.add(new Message(PROJECT_SYNCING_ERROR_GROUP, Message.Type.ERROR, cause), hyperlink);

      return PreSyncCheckResult.failure(cause);
    }

    return PreSyncCheckResult.success();
  }

  private static void attemptToUseSupportedLocalGradle(@NotNull FullRevision modelVersion,
                                                       @NotNull GradleProjectSettings gradleSettings,
                                                       @NotNull Project project) {
    String gradleHome = gradleSettings.getGradleHome();

    FullRevision gradleVersion = null;
    boolean askToSwitchToWrapper = false;
    if (StringUtil.isEmpty(gradleHome)) {
      // Unable to obtain the path of the Gradle local installation. Continue with sync.
      askToSwitchToWrapper = true;
    }
    else {
      File gradleHomePath = new File(gradleHome);
      gradleVersion = GradleUtil.getGradleVersion(gradleHomePath);

      if (gradleVersion == null) {
        askToSwitchToWrapper = true;
      }
    }

    if (!askToSwitchToWrapper) {
      askToSwitchToWrapper = !GradleUtil.isSupportedGradleVersion(gradleVersion);
    }

    if (askToSwitchToWrapper) {
      String msg = getMinimumGradleVersionErrorPrefix(modelVersion) +
                   "A local Gradle distribution was not found, or was not properly set in the IDE.\n\n" +
                   "Would you like your project to use the Gradle wrapper instead?\n" +
                   "(The wrapper will automatically download the latest supported Gradle version).\n\n" +
                   "Click 'OK' to use the Gradle wrapper, or 'Cancel' to manually set the path of a local Gradle distribution.";
      int answer = Messages.showOkCancelDialog(project, msg, GRADLE_SYNC_MSG_TITLE, Messages.getQuestionIcon());

      if (answer == Messages.OK) {
        try {
          File projectDirPath = new File(project.getBasePath());
          GradleUtil.createGradleWrapper(projectDirPath);
          gradleSettings.setDistributionType(DEFAULT_WRAPPED);
        }
        catch (IOException e) {
          LOG.warn("Failed to update Gradle wrapper file to Gradle version " + GRADLE_LATEST_VERSION, e);
        }
        return;
      }

      ChooseGradleHomeDialog dialog = new ChooseGradleHomeDialog();
      dialog.setTitle(String.format("Please select the location of a Gradle distribution version %1$s or newer", GRADLE_MINIMUM_VERSION));
      if (dialog.showAndGet()) {
        String enteredGradleHomePath = dialog.getEnteredGradleHomePath();
        gradleSettings.setGradleHome(enteredGradleHomePath);
      }
    }
  }

  @NotNull
  private static String getMinimumGradleVersionErrorPrefix(@NotNull FullRevision modelVersion) {
    return "Version " + modelVersion + " of the Android Gradle plug-in requires Gradle " + GRADLE_MINIMUM_VERSION + " or newer.\n";
  }

  static class PreSyncCheckResult {
    private final boolean mySuccess;
    @Nullable private final String myFailureCause;

    @NotNull
    private static PreSyncCheckResult success() {
      return new PreSyncCheckResult(true, null);
    }

    @NotNull
    private static PreSyncCheckResult failure(@NotNull String cause) {
      return new PreSyncCheckResult(false, cause);
    }

    private PreSyncCheckResult(boolean success, @Nullable String failureCause) {
      mySuccess = success;
      myFailureCause = failureCause;
    }

    public boolean isSuccess() {
      return mySuccess;
    }

    @Nullable
    public String getFailureCause() {
      return myFailureCause;
    }
  }
}
