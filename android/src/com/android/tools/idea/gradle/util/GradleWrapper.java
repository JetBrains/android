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
package com.android.tools.idea.gradle.util;

import com.android.SdkConstants;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.PropertiesFiles.savePropertiesToFile;
import static com.intellij.openapi.util.io.FileUtil.copyDirContent;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;

public final class GradleWrapper {
  @NonNls public static final String GRADLEW_PROPERTIES_PATH = join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_PROPERTIES);
  private static final Pattern GRADLE_DISTRIBUTION_URL_PATTERN = Pattern.compile(".*/gradle-([^-]+)(-[^\\/\\\\]+)?-(bin|all).zip");

  @NotNull private final File myPropertiesFilePath;
  @Nullable private final Project myProject;

  @Nullable
  public static GradleWrapper find(@NotNull Project project) {
    String basePath = project.getBasePath();
    if (basePath == null) {
      // Default project. Unlikely to happen.
      return null;
    }
    File baseDir = new File(basePath);
    File propertiesFilePath = getDefaultPropertiesFilePath(baseDir);
    return propertiesFilePath.isFile() ? new GradleWrapper(propertiesFilePath, project) : null;
  }

  @NotNull
  public static GradleWrapper get(@NotNull File propertiesFilePath) {
    return new GradleWrapper(propertiesFilePath, null);
  }

  /**
   * Creates the Gradle wrapper, using the latest supported version of Gradle, in the project at the given directory.
   *
   * @param projectPath the project's root directory.
   * @return an instance of {@code GradleWrapper} if the project already has the wrapper or the wrapper was successfully created;
   * {@code null} if the wrapper was not created (e.g. the template files for the wrapper were not found.)
   * @throws IOException any unexpected I/O error.
   * @see SdkConstants#GRADLE_LATEST_VERSION
   */
  public static GradleWrapper create(@NotNull File projectPath) throws IOException {
    return create(projectPath, GRADLE_LATEST_VERSION);
  }

  /**
   * Creates the Gradle wrapper in the project at the given directory.
   *
   * @param projectPath   the project's root directory.
   * @param gradleVersion the version of Gradle to use.
   * @return an instance of {@code GradleWrapper} if the project already has the wrapper or the wrapper was successfully created;
   * {@code null} if the wrapper was not created (e.g. the template files for the wrapper were not found.)
   * @throws IOException any unexpected I/O error.
   * @see SdkConstants#GRADLE_LATEST_VERSION
   */
  public static GradleWrapper create(@NotNull File projectPath, @NotNull String gradleVersion) throws IOException {
    File wrapperFolderPath = new File(projectPath, FD_GRADLE_WRAPPER);
    if (!wrapperFolderPath.isDirectory()) {
      File srcFolderPath = new File(TemplateManager.getTemplateRootFolder(), FD_GRADLE_WRAPPER);
      if (!srcFolderPath.exists()) {
        for (File root : TemplateManager.getExtraTemplateRootFolders()) {
          srcFolderPath = new File(root, FD_GRADLE_WRAPPER);
          if (srcFolderPath.exists()) {
            break;
          }
          else {
            srcFolderPath = null;
          }
        }
      }
      if (srcFolderPath == null) {
        return null;
      }
      copyDirContent(srcFolderPath, projectPath);
    }
    File propertiesFilePath = getDefaultPropertiesFilePath(projectPath);
    GradleWrapper gradleWrapper = get(propertiesFilePath);
    gradleWrapper.updateDistributionUrl(gradleVersion);
    return gradleWrapper;
  }

  private GradleWrapper(@NotNull File propertiesFilePath, @Nullable Project project) {
    myProject = project;
    myPropertiesFilePath = propertiesFilePath;
  }

  @NotNull
  public File getPropertiesFilePath() {
    return myPropertiesFilePath;
  }

  @Nullable
  public VirtualFile getPropertiesFile() {
    return findFileByIoFile(myPropertiesFilePath, true);
  }

  @NotNull
  public static File getDefaultPropertiesFilePath(@NotNull File projectPath) {
    return new File(projectPath, GRADLEW_PROPERTIES_PATH);
  }

  /**
   * Updates the 'distributionUrl' in the Gradle wrapper properties file. An unexpected errors that occur while updating the file will be
   * displayed in an error dialog.
   *
   * @param gradleVersion  the Gradle version to update the property to.
   * @return {@code true} if the property was updated, or {@code false} if no update was necessary because the property already had the
   * correct value.
   */
  public boolean updateDistributionUrlAndDisplayFailure(@NotNull String gradleVersion) {
    try {
      boolean updated = updateDistributionUrl(gradleVersion);
      if (updated) {
        VirtualFile virtualFile = findFileByIoFile(myPropertiesFilePath, true);
        if (virtualFile != null) {
          virtualFile.refresh(false, false);
        }
        return true;
      }
    }
    catch (IOException e) {
      String msg = String.format("Unable to update Gradle wrapper to use Gradle %1$s\n", gradleVersion);
      msg += e.getMessage();
      Messages.showErrorDialog(myProject, msg, "Unexpected Error");
    }
    return false;
  }

  /**
   * Updates the 'distributionUrl' in the given Gradle wrapper properties file.
   *
   * @param gradleVersion  the Gradle version to update the property to.
   * @return {@code true} if the property was updated, or {@code false} if no update was necessary because the property already had the
   * correct value.
   * @throws IOException if something goes wrong when saving the file.
   */
  public boolean updateDistributionUrl(@NotNull String gradleVersion) throws IOException {
    Properties properties = getProperties();
    String distributionUrl = getDistributionUrl(gradleVersion, false);
    String property = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
    if (property != null && (property.equals(distributionUrl) || property.equals(getDistributionUrl(gradleVersion, true)))) {
      return false;
    }
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, distributionUrl);
    savePropertiesToFile(properties, myPropertiesFilePath, null);
    return true;
  }

  @TestOnly
  public void updateDistributionUrl(@NotNull File gradleDistribution) throws IOException {
    String path = gradleDistribution.getPath();
    if (!extensionEquals(path, "zip")) {
      throw new IllegalArgumentException("'" + path + "' should be a zip file");
    }
    Properties properties = getProperties();
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, gradleDistribution.toURI().toURL().toString());
    savePropertiesToFile(properties, myPropertiesFilePath, null);
  }

  @NotNull
  public Properties getProperties() throws IOException {
    return PropertiesFiles.getProperties(myPropertiesFilePath);
  }

  @Nullable
  public String getGradleVersion() throws IOException {
    String url = getProperties().getProperty(DISTRIBUTION_URL_PROPERTY);
    if (url != null) {
      Matcher m = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(url);
      if (m.matches()) {
        return m.group(1);
      }
    }
    return null;
  }

  @VisibleForTesting
  @NotNull
  static String getDistributionUrl(@NotNull String gradleVersion, boolean binOnly) {
    // See https://code.google.com/p/android/issues/detail?id=357944
    String folderName = isSnapshot(gradleVersion) ? "distributions-snapshots" : "distributions";
    String suffix = binOnly ? "bin" : "all";
    return String.format("https://services.gradle.org/%1$s/gradle-%2$s-%3$s.zip", folderName, gradleVersion, suffix);
  }

  @VisibleForTesting
  static boolean isSnapshot(@NotNull String gradleVersion) {
    return gradleVersion.indexOf('-') != -1 && gradleVersion.endsWith("+0000");
  }
}
