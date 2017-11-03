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
package com.android.tools.idea.gradle.project.sync.cleanup;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.ChooseGradleHomeDialog;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.gradle.util.GradleUtil.isSupportedGradleVersion;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.ui.Messages.*;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

class GradleDistributionCleanUpTask extends ProjectCleanUpTask {
  private static final String GRADLE_SYNC_MSG_TITLE = "Gradle Sync";

  @Override
  void cleanUp(@NotNull Project project) {
    GradleProjectSettings gradleSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
    GradleWrapper gradleWrapper = GradleWrapper.find(project);

    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;
    boolean usingWrapper = (distributionType == null || distributionType == DEFAULT_WRAPPED) && gradleWrapper != null;
    if (usingWrapper && gradleSettings != null) {
      // Do this just to ensure that the right distribution type is set. If this is not set, build.gradle editor will not have code
      // completion (see BuildClasspathModuleGradleDataService, line 119).
      gradleSettings.setDistributionType(DEFAULT_WRAPPED);
    }
    else if (gradleWrapper == null && gradleSettings != null) {
      createWrapperIfNecessary(project, gradleSettings, distributionType);
    }
  }

  private boolean createWrapperIfNecessary(@NotNull Project project,
                                           @NotNull GradleProjectSettings gradleSettings,
                                           @Nullable DistributionType distributionType) {
    boolean createWrapper = false;
    boolean chooseLocalGradleHome = false;

    if (distributionType == null) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return true;
      }
      String msg = createUseWrapperQuestion("Gradle settings for this project are not configured yet.");
      int answer = showOkCancelDialog(project, msg, GRADLE_SYNC_MSG_TITLE, getQuestionIcon());
      createWrapper = answer == OK;
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
        File gradleHomePath = toSystemDependentPath(gradleHome);
        if (!gradleHomePath.isDirectory()) {
          String reason = String.format("The path\n'%1$s'\n, set as a local Gradle distribution, does not belong to an existing directory.",
                                        gradleHomePath.getPath());
          msg = createUseWrapperQuestion(reason);
        }
        else {
          GradleVersion gradleVersion = GradleVersions.getInstance().getGradleVersion(gradleHomePath);
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
        createWrapper = answer == OK;
        chooseLocalGradleHome = !createWrapper;
      }
    }

    if (createWrapper) {
      File projectPath = getBaseDirPath(project);

      // attempt to delete the whole gradle wrapper folder.
      File gradleDirPath = new File(projectPath, SdkConstants.FD_GRADLE);
      if (!delete(gradleDirPath)) {
        // deletion failed. Let sync continue.
        return true;
      }

      try {
        GradleWrapper.create(projectPath);
        if (distributionType == null) {
          gradleSettings.setDistributionType(DEFAULT_WRAPPED);
        }
        return true;
      }
      catch (IOException e) {
        Logger.getInstance(getClass()).info("Failed to create Gradle wrapper for project '" + project.getName() + "'", e);
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
}
