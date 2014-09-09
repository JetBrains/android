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
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PreSyncChecks {
  private static final Pattern ANDROID_GRADLE_PLUGIN_DEPENDENCY_PATTERN = Pattern.compile("['\"]com.android.tools.build:gradle:(.+)['\"]");
  private static final Pattern GRADLE_DISTRIBUTION_URL_PATTERN =
    Pattern.compile("http://services\\.gradle\\.org/distributions/gradle-(.+)-(.+)\\.zip");

  private static final Logger LOG = Logger.getInstance(PreSyncChecks.class);

  private static final String MINIMUM_SUPPORTED_GRADLE_VERSION_FOR_PLUGIN_0_13 = "2.0";
  private static final FullRevision MINIMUM_SUPPORTED_GRADLE_REVISION_FOR_PLUGIN_0_13 =
    FullRevision.parseRevision(MINIMUM_SUPPORTED_GRADLE_VERSION_FOR_PLUGIN_0_13);

  private PreSyncChecks() {
  }

  static void check(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      return;
    }
    final List<File> filesToProcess = Lists.newArrayList();
    VfsUtil.processFileRecursivelyWithoutIgnored(baseDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (SdkConstants.FN_BUILD_GRADLE.equals(virtualFile.getName())) {
          filesToProcess.add(VfsUtilCore.virtualToIoFile(virtualFile));
        }
        return true;
      }
    });

    createGradleWrapperIfMissing(project);
    attemptToUpdateGradleVersionIfApplicable(project, filesToProcess);
  }

  private static void createGradleWrapperIfMissing(@NotNull Project project) {
    GradleProjectSettings gradleSettings = GradleUtil.getGradleProjectSettings(project);

    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;
    if (distributionType == DistributionType.DEFAULT_WRAPPED) {
      File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);
      if (wrapperPropertiesFile == null) {
        File projectDirPath = new File(project.getBasePath());

        // attempt to delete the whole gradle wrapper folder.
        File gradleDirPath = new File(projectDirPath, SdkConstants.FD_GRADLE);
        if (!FileUtil.delete(gradleDirPath)) {
          // deletion failed. Let sync continue.
          return;
        }

        try {
          GradleUtil.createGradleWrapper(projectDirPath, null /* use latest supported version of Gradle */);
        }
        catch (IOException e) {
          LOG.info("Failed to create Gradle wrapper for project '" + project.getName() + "'", e);
        }
      }
    }
  }

  private static void attemptToUpdateGradleVersionIfApplicable(@NotNull Project project, @NotNull List<File> gradleFiles) {
    String originalPluginVersion = null;
    for (File fileToCheck : gradleFiles) {
      if (SdkConstants.FN_BUILD_GRADLE.equals(fileToCheck.getName())) {
        try {
          String contents = Files.toString(fileToCheck, Charsets.UTF_8);
          Matcher matcher = ANDROID_GRADLE_PLUGIN_DEPENDENCY_PATTERN.matcher(contents);
          if (matcher.find()) {
            originalPluginVersion = matcher.group(1);
            if (!StringUtil.isEmpty(originalPluginVersion)) {
              break;
            }
          }
        }
        catch (IOException e) {
          LOG.warn("Failed to read contents of " + fileToCheck.getPath());
        }
      }
    }
    if (StringUtil.isEmpty(originalPluginVersion)) {
      // Could not obtain plug-in version. Continue with sync.
      return;
    }
    String pluginVersion = originalPluginVersion.replace('+', '0');
    FullRevision pluginRevision = null;
    try {
      pluginRevision = FullRevision.parseRevision(pluginVersion);
    }
    catch (NumberFormatException e) {
      LOG.warn("Failed to parse '" + pluginVersion + "'");
    }
    if (pluginRevision == null || (pluginRevision.getMajor() == 0 && pluginRevision.getMinor() <= 12)) {
      // Unable to parse the plug-in version, or the plug-in is version 0.12 or older. Continue with sync.
      return;
    }

    GradleProjectSettings gradleSettings = GradleUtil.getGradleProjectSettings(project);
    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);

    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;

    boolean usingWrapper = distributionType == DistributionType.DEFAULT_WRAPPED && wrapperPropertiesFile != null;
    if (usingWrapper) {
      attemptToUpdateGradleVersionInWrapper(wrapperPropertiesFile, originalPluginVersion, project);
    }
    else if (distributionType == DistributionType.LOCAL) {
      attemptToUseSupportedLocalGradle(gradleSettings);
    }
  }

  private static void attemptToUpdateGradleVersionInWrapper(@NotNull final File wrapperPropertiesFile,
                                                            @NotNull String pluginVersion,
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

    if (!isSupportedGradleVersion(gradleRevision)) {
      String newGradleVersion = MINIMUM_SUPPORTED_GRADLE_VERSION_FOR_PLUGIN_0_13;
      String msg = "Version " + pluginVersion + " of the Android Gradle plug-in requires Gradle " + newGradleVersion + " or newer.\n\n" +
                   "Click 'OK' to automatically update the Gradle version in the Gradle wrapper and continue.";
      Messages.showMessageDialog(project, msg, "Gradle Sync", Messages.getQuestionIcon());
      try {
        GradleUtil.updateGradleDistributionUrl(newGradleVersion, wrapperPropertiesFile);
      }
      catch (IOException e) {
        LOG.warn("Failed to update Gradle wrapper file to Gradle version " + newGradleVersion);
      }
    }
  }

  private static void attemptToUseSupportedLocalGradle(@NotNull GradleProjectSettings gradleSettings) {
    String gradleHome = gradleSettings.getGradleHome();
    if (StringUtil.isEmpty(gradleHome)) {
      // Unable to obtain the path of the Gradle local installation. Continue with sync.
      return;
    }
    File gradleHomePath = new File(gradleHome);
    FullRevision gradleVersion = GradleUtil.getGradleVersion(gradleHomePath);

    if (gradleVersion == null) {
      // Unable to obtain the path of the Gradle local installation. Continue with sync.
      return;
    }

    if (!isSupportedGradleVersion(gradleVersion)) {
      String newGradleVersion = MINIMUM_SUPPORTED_GRADLE_VERSION_FOR_PLUGIN_0_13;
      ChooseGradleHomeDialog dialog = new ChooseGradleHomeDialog(newGradleVersion);
      dialog.setTitle(String.format("Please select the location of a Gradle distribution version %1$s or newer", newGradleVersion));
      if (dialog.showAndGet()) {
        String enteredGradleHomePath = dialog.getEnteredGradleHomePath();
        gradleSettings.setGradleHome(enteredGradleHomePath);
      }
    }
  }

  private static boolean isSupportedGradleVersion(@NotNull FullRevision gradleVersion) {
    // Plug-in v0.13.+ supports Gradle 2.0+ only.
    return gradleVersion.compareTo(MINIMUM_SUPPORTED_GRADLE_REVISION_FOR_PLUGIN_0_13) >= 0;
  }
}
