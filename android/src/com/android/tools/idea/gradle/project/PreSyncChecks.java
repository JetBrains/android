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
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.PropertiesUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PreSyncChecks {
  private static final Pattern GRADLE_DISTRIBUTION_URL_PATTERN =
    Pattern.compile("http://services\\.gradle\\.org/distributions/gradle-(.+)-(.+)\\.zip");

  private static final Logger LOG = Logger.getInstance(PreSyncChecks.class);

  private static final String GRADLE_SYNC_MSG_TITLE = "Gradle Sync";

  private PreSyncChecks() {
  }

  static boolean canSync(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      // Unlikely to happen because it would mean this is the default project.
      return true;
    }

    try {
      if (hasEmptySettingsFile(project)) {
        String msg = "Unable to sync project with Gradle. The file 'settings.gradle' does not specify any modules (sub-projects.)";
        Messages.showErrorDialog(project, msg, GRADLE_SYNC_MSG_TITLE);
        return false;
      }
    }
    catch (IOException e) {
      // Failed to read settings.gradle, ask user if she would like to continue.
      String msg = "Failed to read contents of settings.gradle file: " + e.getMessage() + ".\n" +
                   "Would you like to continue? (Project sync may never stop if the file is empty.)";
      int answer = Messages.showYesNoDialog(project, msg, GRADLE_SYNC_MSG_TITLE, Messages.getErrorIcon());
      return answer == Messages.YES;
    }

    FullRevision modelVersion = GradleUtil.getResolvedAndroidGradleModelVersion(project);
    ensureCorrectGradleSettings(project, modelVersion);
    return true;
  }

  @VisibleForTesting
  static boolean hasEmptySettingsFile(@NotNull Project project) throws IOException {
    File settingsFile = new File(project.getBasePath(), SdkConstants.FN_SETTINGS_GRADLE);
    if (!settingsFile.isFile()) {
      return false;
    }
    String text = FileUtil.loadFile(settingsFile);
    if (StringUtil.isEmptyOrSpaces(text)) {
      // empty file (maybe with spaces only)
      return true;
    }

    GroovyLexer lexer = new GroovyLexer();
    lexer.start(text);
    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      if (type == GroovyTokenTypes.mIDENT && "include".equals(lexer.getTokenText())) {
        // most likely this is a module (e.g. include ":app")
        return false;
      }
      lexer.advance();
    }
    return true;
  }

  private static void ensureCorrectGradleSettings(@NotNull Project project, @Nullable FullRevision modelVersion) {
    if (modelVersion == null || createWrapperIfNecessary(project, modelVersion)) {
      return;
    }

    GradleProjectSettings gradleSettings = GradleUtil.getGradleProjectSettings(project);
    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);

    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;

    boolean usingWrapper =
      (distributionType == null || distributionType == DistributionType.DEFAULT_WRAPPED) && wrapperPropertiesFile != null;
    if (usingWrapper) {
      attemptToUpdateGradleVersionInWrapper(wrapperPropertiesFile, modelVersion, project);
    }
    else if (distributionType == DistributionType.LOCAL) {
      attemptToUseSupportedLocalGradle(modelVersion, gradleSettings, project);
    }
  }

  // Returns true if wrapper was created or sync should continue immediately after executing this method.
  private static boolean createWrapperIfNecessary(@NotNull Project project, @Nullable FullRevision modelVersion) {
    GradleProjectSettings gradleSettings = GradleUtil.getGradleProjectSettings(project);
    if (gradleSettings == null) {
      // Unlikely to happen. When we get to this point we already created GradleProjectSettings.
      return true;
    }

    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);

    if (wrapperPropertiesFile == null) {
      String gradleVersion = null;
      if (modelVersion != null) {
        gradleVersion = GradleUtil.getSupportedGradleVersion(modelVersion);
      }

      DistributionType distributionType = gradleSettings.getDistributionType();
      boolean createWrapper = false;
      if (distributionType == null) {
        String msg = "Gradle settings for this project are not configured yet.\n\n" +
                     "Would you like the project to use the Gradle wrapper?\n" +
                     "(The wrapper will automatically download the latest supported Gradle version).\n\n" +
                     "Click 'OK' to use the Gradle wrapper, or 'Cancel' to manually set the path of a local Gradle distribution.";
        int answer = Messages.showOkCancelDialog(project, msg, GRADLE_SYNC_MSG_TITLE, Messages.getQuestionIcon());
        createWrapper = answer == Messages.OK;

      } else if (distributionType == DistributionType.DEFAULT_WRAPPED) {
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
          GradleUtil.createGradleWrapper(projectDirPath, gradleVersion);
          if (distributionType == null) {
            gradleSettings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
          }
          return true;
        }
        catch (IOException e) {
          LOG.info("Failed to create Gradle wrapper for project '" + project.getName() + "'", e);
        }
      }
      else if (distributionType == null) {
        ChooseGradleHomeDialog dialog = new ChooseGradleHomeDialog(gradleVersion);
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

  private static void attemptToUpdateGradleVersionInWrapper(@NotNull final File wrapperPropertiesFile,
                                                            @NotNull FullRevision modelVersion,
                                                            @NotNull Project project) {
    Properties wrapperProperties = null;
    try {
      wrapperProperties = PropertiesUtil.getProperties(wrapperPropertiesFile);
    }
    catch (IOException e) {
      LOG.warn("Failed to read file " + wrapperPropertiesFile.getPath());
    }

    if (wrapperProperties == null) {
      // There is a wrapper, but the Gradle version could not be read. Continue with sync.
      return;
    }
    String url = wrapperProperties.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY);
    Matcher matcher = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(url);
    if (!matcher.matches()) {
      // Could not get URL of Gradle distribution. Continue with sync.
      return;
    }
    String gradleVersion = matcher.group(1);
    FullRevision gradleRevision = FullRevision.parseRevision(gradleVersion);

    if (!isSupportedGradleVersion(modelVersion, gradleRevision)) {
      String newGradleVersion = GradleUtil.getSupportedGradleVersion(modelVersion);
      assert newGradleVersion != null;
      String msg = "Version " + modelVersion + " of the Android Gradle plug-in requires Gradle " + newGradleVersion + " or newer.\n\n" +
                   "Click 'OK' to automatically update the Gradle version in the Gradle wrapper and continue.";
      Messages.showMessageDialog(project, msg, GRADLE_SYNC_MSG_TITLE, Messages.getQuestionIcon());
      try {
        GradleUtil.updateGradleDistributionUrl(newGradleVersion, wrapperPropertiesFile);
      }
      catch (IOException e) {
        LOG.warn("Failed to update Gradle wrapper file to Gradle version " + newGradleVersion);
      }
    }
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
      askToSwitchToWrapper = !isSupportedGradleVersion(modelVersion, gradleVersion);
    }

    if (askToSwitchToWrapper) {
      String newGradleVersion = GradleUtil.getSupportedGradleVersion(modelVersion);

      String msg = "Version " + modelVersion + " of the Android Gradle plug-in requires Gradle " + newGradleVersion + " or newer.\n" +
                   "A local Gradle distribution was not found, or was not properly set in the IDE.\n\n" +
                   "Would you like your project to use the Gradle wrapper instead?\n" +
                   "(The wrapper will automatically download the latest supported Gradle version).\n\n" +
                   "Click 'OK' to use the Gradle wrapper, or 'Cancel' to manually set the path of a local Gradle distribution.";
      int answer = Messages.showOkCancelDialog(project, msg, GRADLE_SYNC_MSG_TITLE, Messages.getQuestionIcon());
      if (answer == Messages.OK) {
        try {
          File projectDirPath = new File(project.getBasePath());
          GradleUtil.createGradleWrapper(projectDirPath, newGradleVersion);
          gradleSettings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
        }
        catch (IOException e) {
          LOG.warn("Failed to update Gradle wrapper file to Gradle version " + newGradleVersion, e);
        }
        return;
      }

      ChooseGradleHomeDialog dialog = new ChooseGradleHomeDialog(newGradleVersion);
      dialog.setTitle(String.format("Please select the location of a Gradle distribution version %1$s or newer", newGradleVersion));
      if (dialog.showAndGet()) {
        String enteredGradleHomePath = dialog.getEnteredGradleHomePath();
        gradleSettings.setGradleHome(enteredGradleHomePath);
      }
    }
  }

  private static boolean isSupportedGradleVersion(@NotNull FullRevision modelVersion, @NotNull FullRevision gradleVersion) {
    String supported = GradleUtil.getSupportedGradleVersion(modelVersion);
    if (supported != null) {
      return gradleVersion.compareTo(FullRevision.parseRevision(supported)) == 0;
    }
    return false;
  }
}
