/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.io.FilePaths;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.PropertiesFiles.getProperties;
import static com.android.tools.idea.gradle.util.PropertiesFiles.savePropertiesToFile;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Utility methods related to a Gradle project's local.properties file.
 */
public final class LocalProperties {
  @NotNull private final File myPropertiesFilePath;
  @NotNull private final File myProjectFolderPath;
  @NotNull private final Properties myProperties;

  @Nullable private File myNewAndroidSdkPath;
  private boolean myAndroidSdkPathModified;

  @Nullable private File myNewGradleJdkPath;
  private boolean myGradleJdkPathModified;

  @Nullable private File myNewAndroidNdkPath;
  private boolean myAndroidNdkPathModified;

  @Nullable private File myNewAndroidCmakePath;
  private boolean myAndroidCmakePathModified;

  /**
   * Creates a new {@link LocalProperties}. If a local.properties file does not exist, a new one will be created when the method
   * {@link #save()} is invoked.
   *
   * @param project the Android project.
   * @throws IOException              if an I/O error occurs while reading the file.
   * @throws IllegalArgumentException if there is already a directory called "local.properties" in the given project.
   */
  public LocalProperties(@NotNull Project project) throws IOException {
    this(getBaseDirPath(project));
  }

  /**
   * Creates a new {@link LocalProperties}. If a local.properties file does not exist, a new one will be created when the method
   * {@link #save()} is invoked.
   *
   * @param projectFolderPath the path of the Android project's root directory.
   * @throws IOException              if an I/O error occurs while reading the file.
   * @throws IllegalArgumentException if there is already a directory called "local.properties" at the given path.
   */
  public LocalProperties(@NotNull File projectFolderPath) throws IOException {
    myProjectFolderPath = projectFolderPath;
    myPropertiesFilePath = new File(projectFolderPath, FN_LOCAL_PROPERTIES);
    myProperties = getProperties(myPropertiesFilePath);
  }

  /**
   * @return the path of the Android SDK specified in this local.properties file; or {@code null} if such property is not specified.
   */
  @Nullable
  public File getAndroidSdkPath() {
    if (myAndroidSdkPathModified) {
      return myNewAndroidSdkPath;
    }
    return getAndroidSdkPathFromFile();
  }

  /**
   * @return the path of the Gradle JDK specified in this local.properties file; or {@code null} if such property is not specified.
   */
  @Nullable
  public File getGradleJdkPath() {
    if (myGradleJdkPathModified) {
      return myNewGradleJdkPath;
    }
    return getGradleJdkPathFromFile();
  }

  /**
   * @return the path of the Android NDK specified in this local.properties file; or {@code null} if such property is not specified.
   */
  @Nullable
  public File getAndroidNdkPath() {
    if (myAndroidNdkPathModified) {
      return myNewAndroidNdkPath;
    }
    return getAndroidNdkPathFromFile();
  }

  /**
   * @return the path of the CMake specified in this local.properties file; or {@code null} if such property is not specified.
   */
  @Nullable
  public File getAndroidCmakePath() {
    if (myAndroidCmakePathModified) {
      return myNewAndroidCmakePath;
    }
    return getAndroidCmakePathFromFile();
  }

  public void setAndroidSdkPath(@NotNull Sdk androidSdk) {
    String androidSdkPath = androidSdk.getHomePath();
    assert androidSdkPath != null;
    setAndroidSdkPath(androidSdkPath);
  }

  public void setAndroidSdkPath(@NotNull String androidSdkPath) {
    setAndroidSdkPath(FilePaths.stringToFile(androidSdkPath));
  }

  public void setAndroidSdkPath(@NotNull File androidSdkPath) {
    myNewAndroidSdkPath = androidSdkPath;
    myAndroidSdkPathModified = true;
  }

  public void setGradleJdkPath(@NotNull String gradleJdkPath) {
    setGradleJdkPath(FilePaths.stringToFile(gradleJdkPath));
  }

  public void setGradleJdkPath(@NotNull File gradleJdkPath) {
    myNewGradleJdkPath = gradleJdkPath;
    myGradleJdkPathModified = true;
  }

  public void setAndroidNdkPath(@NotNull String androidNdkPath) {
    setAndroidNdkPath(FilePaths.stringToFile(androidNdkPath));
  }

  public void setAndroidNdkPath(@Nullable File androidNdkPath) {
    myNewAndroidNdkPath = androidNdkPath;
    myAndroidNdkPathModified = true;
  }

  public void setAndroidCmakePath(@Nullable File androidNdkPath) {
    myNewAndroidCmakePath = androidNdkPath;
    myAndroidCmakePathModified = true;
  }

  public boolean hasAndroidDirProperty() {
    String property = getProperty("android.dir");
    return !isNullOrEmpty(property);
  }

  @Nullable
  public String getProperty(@NotNull String key) {
    return myProperties.getProperty(key);
  }

  /**
   * Saves any changes to the underlying local.properties file.
   */
  public void save() throws IOException {
    setPathIfApplicable(myAndroidSdkPathModified, SDK_DIR_PROPERTY, getAndroidSdkPathFromFile(), myNewAndroidSdkPath);
    setPathIfApplicable(myGradleJdkPathModified, GRADLE_JDK_DIR_PROPERTY, getGradleJdkPathFromFile(), myNewGradleJdkPath);
    setPathIfApplicable(myAndroidNdkPathModified, NDK_DIR_PROPERTY, getAndroidNdkPathFromFile(), myNewAndroidNdkPath);
    setPathIfApplicable(myAndroidCmakePathModified, CMAKE_DIR_PROPERTY, getAndroidCmakePathFromFile(), myNewAndroidCmakePath);

    if (myAndroidSdkPathModified || myGradleJdkPathModified || myAndroidNdkPathModified || myAndroidCmakePathModified) {
      savePropertiesToFile(myProperties, myPropertiesFilePath, getHeaderComment());
    }
    // reset "modified" state.
    myNewAndroidCmakePath = myNewAndroidSdkPath = myNewGradleJdkPath = myNewAndroidNdkPath = null;
    myAndroidCmakePathModified = myAndroidSdkPathModified = myGradleJdkPathModified = myAndroidNdkPathModified = false;
  }

  private void setPathIfApplicable(boolean pathModified, @NotNull String propertyName, @Nullable File currentPath, @Nullable File newPath) {
    if (pathModified && !filesEqual(currentPath, newPath)) {
      String path = newPath != null ? newPath.getPath() : null;
      if (isNotEmpty(path)) {
        myProperties.setProperty(propertyName, path);
      }
      else {
        myProperties.remove(propertyName);
      }
    }
  }

  @Nullable
  private File getAndroidSdkPathFromFile() {
    return getPath(SDK_DIR_PROPERTY);
  }

  @Nullable
  private File getGradleJdkPathFromFile() {
    return getPath(GRADLE_JDK_DIR_PROPERTY);
  }

  @Nullable
  private File getAndroidNdkPathFromFile() {
    return getPath(NDK_DIR_PROPERTY);
  }

  @Nullable
  private File getAndroidCmakePathFromFile() {
    return getPath(CMAKE_DIR_PROPERTY);
  }

  /**
   * @return the path for the given property name specified in this local.properties file; or {@code null} if such property is not specified.
   */
  @Nullable
  private File getPath(String property) {
    String path = getProperty(property);
    if (isNotEmpty(path)) {
      if (!isAbsolute(path)) {
        String canonicalPath = toCanonicalPath(new File(myProjectFolderPath, toSystemDependentName(path)).getPath());
        File file = new File(canonicalPath);
        if (!file.isDirectory()) {
          // Only accept resolved relative paths if they exist, otherwise just use the path as it was declared in local.properties.
          // When getting a path from another platform (e.g. a Windows path when opening a project on Mac), java.io.File will think that the
          // path is relative and it will prepend the path of the project to it.
          // See https://code.google.com/p/android/issues/detail?id=82184
          return new File(path);
        }
      }
      return FilePaths.stringToFile(path);
    }
    return null;
  }

  @NotNull
  private static String getHeaderComment() {
    String[] lines = {
      "# This file must *NOT* be checked into Version Control Systems,",
      "# as it contains information specific to your local configuration.",
      "",
      "# Location of the SDK. This is only used by Gradle.",
      "# For customization when using a Version Control System, please read the",
      "# header note."
    };
    return Joiner.on(System.lineSeparator()).join(lines);
  }

  @NotNull
  public File getPropertiesFilePath() {
    return myPropertiesFilePath;
  }

  @VisibleForTesting
  @NotNull
  Properties properties() {
    return myProperties;
  }
}
