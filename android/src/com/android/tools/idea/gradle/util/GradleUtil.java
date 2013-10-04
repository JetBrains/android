/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.io.Closeables;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Utilities related to Gradle.
 */
public final class GradleUtil {
  @NonNls public static final String GRADLE_MINIMUM_VERSION = "1.8";
  @NonNls public static final String GRADLE_LATEST_VERSION = GRADLE_MINIMUM_VERSION;

  @NonNls public static final String GRADLE_PLUGIN_MINIMUM_VERSION = "0.6.1";
  @NonNls public static final String GRADLE_PLUGIN_LATEST_VERSION = "0.6.+";

  @NonNls private static final String GRADLEW_PROPERTIES_PATH =
    "gradle" + File.separator + "wrapper" + File.separator + "gradle-wrapper.properties";
  @NonNls private static final String GRADLEW_DISTRIBUTION_URL_PROPERTY_NAME = "distributionUrl";

  private GradleUtil() {
  }

  @NotNull
  public static File getGradleWrapperPropertiesFilePath(@NotNull File projectRootDir) {
    return new File(projectRootDir, GRADLEW_PROPERTIES_PATH);
  }

  public static void updateGradleDistributionUrl(@NotNull String gradleVersion, @NotNull File propertiesFile) throws IOException {
    Properties properties = loadGradleWrapperProperties(propertiesFile);
    String gradleDistributionUrl = getGradleDistributionUrl(gradleVersion);
    if (gradleDistributionUrl.equals(properties.getProperty(GRADLEW_DISTRIBUTION_URL_PROPERTY_NAME))) {
      return;
    }
    properties.setProperty(GRADLEW_DISTRIBUTION_URL_PROPERTY_NAME, gradleDistributionUrl);
    FileOutputStream out = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      out = new FileOutputStream(propertiesFile);
      properties.store(out, null);
    }
    finally {
      Closeables.closeQuietly(out);
    }
  }

  @NotNull
  private static Properties loadGradleWrapperProperties(@NotNull File propertiesFile) throws IOException {
    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(propertiesFile);
      properties.load(fileInputStream);
      return properties;
    }
    finally {
      Closeables.closeQuietly(fileInputStream);
    }
  }

  @NotNull
  private static String getGradleDistributionUrl(@NotNull String gradleVersion) {
    return String.format("http://services.gradle.org/distributions/gradle-%1$s-bin.zip", gradleVersion);
  }
}
