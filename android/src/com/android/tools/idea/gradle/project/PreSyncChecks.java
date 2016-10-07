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
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenProjectStructureHyperlink;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.ProxySettings;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.project.SdkSync.syncIdeAndProjectAndroidSdks;
import static com.android.tools.idea.gradle.service.notification.hyperlink.JdkQuickFixes.getJdkQuickFixes;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.checkForJdk;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.ui.Messages.showOkCancelDialog;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

final class PreSyncChecks {
  private static final Logger LOG = Logger.getInstance(PreSyncChecks.class);
  private static final String GRADLE_SYNC_MSG_TITLE = "Gradle Sync";
  private static final String PROJECT_SYNC_ERROR_GROUP = "Project sync error";

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
    syncMessages.removeMessages(PROJECT_SYNC_ERROR_GROUP);

    if (isAndroidStudio()) {
      try {
        syncIdeAndProjectAndroidSdks(project);
      }
      catch (IOException e) {
        String msg = "Failed to sync SDKs";
        LOG.info(msg, e);
        return PreSyncCheckResult.failure(msg + ": " + e.getMessage());
      }

      // We only check jdk for Studio, because only Studio uses the same JDK for all modules and all Gradle invocations.
      // See https://code.google.com/p/android/issues/detail?id=172714
      Sdk jdk = IdeSdks.getJdk();
      if (jdk == null || !checkForJdk(project, jdk.getName())) {
        AndroidGradleNotification.getInstance(project).showBalloon("Invalid Project JDK", "Please choose a valid JDK directory", ERROR,
                                                                   OpenProjectStructureHyperlink.openJdkSettings(project));
        return PreSyncCheckResult.failure("Invalid Project Jdk");
      }

      // We only check proxy settings for Studio, because Studio does not pass the IDE's proxy settings to Gradle.
      // See https://code.google.com/p/android/issues/detail?id=169743
      checkHttpProxySettings(project);

      if (!Jdks.isApplicableJdk(jdk, JDK_1_8)) {
        Message message = new Message(PROJECT_SYNC_ERROR_GROUP, Message.Type.ERROR, "Please use JDK 8 or newer.");

        List<NotificationHyperlink> quickFixes = getJdkQuickFixes(project);
        syncMessages.add(message, quickFixes.toArray(new NotificationHyperlink[quickFixes.size()]));
        return PreSyncCheckResult.failure("Invalid Project Jdk");
      }
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      attemptToUseEmbeddedGradle(project);
    }

    GradleProjectSettings gradleSettings = getGradleProjectSettings(project);
    File wrapperPropertiesFile = findWrapperPropertiesFile(project);

    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;
    boolean usingWrapper = (distributionType == null || distributionType == DEFAULT_WRAPPED) && wrapperPropertiesFile != null;
    if (usingWrapper && gradleSettings != null) {
      // Do this just to ensure that the right distribution type is set. If this is not set, build.gradle editor will not have code
      // completion (see BuildClasspathModuleGradleDataService, line 119).
      gradleSettings.setDistributionType(DEFAULT_WRAPPED);
    }
    else if (!ApplicationManager.getApplication().isUnitTestMode()) {
      if (wrapperPropertiesFile == null && gradleSettings != null) {
        createWrapperIfNecessary(project, gradleSettings, distributionType);
      }
    }

    return PreSyncCheckResult.success();
  }

  // If the IDE is configured to use proxies, we ask the user if she would like to have those settings copied to gradle.properties, if such
  // files does not include them already.
  // Gradle may need those settings to access the Internet to download dependencies.
  // See https://code.google.com/p/android/issues/detail?id=65325
  private static void checkHttpProxySettings(@NotNull Project project) {
    HttpConfigurable ideHttpProxySettings = HttpConfigurable.getInstance();
    if (ideHttpProxySettings.USE_HTTP_PROXY && isNotEmpty(ideHttpProxySettings.PROXY_HOST)) {
      GradleProperties properties;
      try {
        properties = new GradleProperties(project);
      }
      catch (IOException e) {
        LOG.info("Failed to read gradle.properties file", e);
        // Let sync continue, even though it may fail.
        return;
      }
      ProxySettings gradleProxySettings = properties.getHttpProxySettings();
      ProxySettings ideProxySettings = new ProxySettings(ideHttpProxySettings);

      if (!ideProxySettings.equals(gradleProxySettings)) {
        ProxySettingsDialog dialog = new ProxySettingsDialog(project, ideProxySettings);
        dialog.show();
        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          dialog.applyProxySettings(properties.getProperties());
          try {
            properties.save();
          }
          catch (IOException e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            Throwable root = getRootCause(e);

            String cause = root.getMessage();
            String errMsg = "Failed to save changes to gradle.properties file.";
            if (isNotEmpty(cause)) {
              if (!cause.endsWith(".")) {
                cause += ".";
              }
              errMsg += String.format("\nCause: %1$s", cause);
            }
            AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);
            notification.showBalloon("Proxy Settings", errMsg, ERROR);

            LOG.info("Failed to save changes to gradle.properties file", e);
          }
        }
      }
    }
  }

  private static boolean createWrapperIfNecessary(@NotNull Project project,
                                                  @NotNull GradleProjectSettings gradleSettings,
                                                  @Nullable DistributionType distributionType) {
    boolean createWrapper = false;
    boolean chooseLocalGradleHome = false;

    if (distributionType == null) {
      String msg = createUseWrapperQuestion("Gradle settings for this project are not configured yet.");
      int answer = showOkCancelDialog(project, msg, GRADLE_SYNC_MSG_TITLE, getQuestionIcon());
      createWrapper = answer == Messages.OK;
    }
    else if (distributionType == DEFAULT_WRAPPED) {
      createWrapper = true;
    }
    else if (distributionType == LOCAL) {
      String gradleHome = gradleSettings.getGradleHome();
      String msg = null;
      if (isEmpty(gradleHome)) {
        msg = createUseWrapperQuestion("The path of the local Gradle distribution to use is not set.");
      }
      else {
        File gradleHomePath = new File(toSystemDependentName(gradleHome));
        if (!gradleHomePath.isDirectory()) {
          String reason = String.format("The path\n'%1$s'\n, set as a local Gradle distribution, does not belong to an existing directory.",
                                        gradleHomePath.getPath());
          msg = createUseWrapperQuestion(reason);
        }
        else {
          GradleVersion gradleVersion = getGradleVersion(gradleHomePath);
          if (gradleVersion == null) {
            String reason = String.format("The path\n'%1$s'\n, does not belong to a Gradle distribution.", gradleHomePath.getPath());
            msg = createUseWrapperQuestion(reason);
          }
          else if (!isSupportedGradleVersion(gradleVersion)) {
            String reason = String.format("Gradle version %1$s is not supported.", gradleHomePath.getPath());
            msg = createUseWrapperQuestion(reason);
          }
        }
      }
      if (msg != null) {
        int answer = showOkCancelDialog(project, msg, GRADLE_SYNC_MSG_TITLE, getQuestionIcon());
        createWrapper = answer == Messages.OK;
        chooseLocalGradleHome = !createWrapper;
      }
    }

    if (createWrapper) {
      File projectDirPath = getBaseDirPath(project);

      // attempt to delete the whole gradle wrapper folder.
      File gradleDirPath = new File(projectDirPath, SdkConstants.FD_GRADLE);
      if (!delete(gradleDirPath)) {
        // deletion failed. Let sync continue.
        return true;
      }

      try {
        createGradleWrapper(projectDirPath);
        if (distributionType == null) {
          gradleSettings.setDistributionType(DEFAULT_WRAPPED);
        }
        return true;
      }
      catch (IOException e) {
        LOG.info("Failed to create Gradle wrapper for project '" + project.getName() + "'", e);
      }
    }
    else if (distributionType == null || chooseLocalGradleHome) {
      ChooseGradleHomeDialog dialog = new ChooseGradleHomeDialog();
      if (dialog.showAndGet()) {
        String enteredGradleHomePath = dialog.getEnteredGradleHomePath();
        gradleSettings.setGradleHome(enteredGradleHomePath);
        gradleSettings.setDistributionType(LOCAL);
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static String createUseWrapperQuestion(@NotNull String reason) {
    return reason + "\n\n" +
           "Would you like the project to use the Gradle wrapper?\n" +
           "(The wrapper will automatically download the latest supported Gradle version).\n\n" +
           "Click 'OK' to use the Gradle wrapper, or 'Cancel' to manually set the path of a local Gradle distribution.";
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
