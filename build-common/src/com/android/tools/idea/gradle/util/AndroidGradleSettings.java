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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Utility methods related to Gradle-specific Android settings.
 */
public final class AndroidGradleSettings {
  private static final Logger LOG = Logger.getInstance(AndroidGradleSettings.class);

  @NonNls private static final String JVM_ARG_FORMAT = "-D%1$s=%2$s";
  @NonNls private static final String PROJECT_PROPERTY_FORMAT = "-P%1$s=%2$s";

  @NonNls public static final String ANDROID_HOME_JVM_ARG = "android.home";

  private AndroidGradleSettings() {
  }

  @NotNull
  public static String createJvmArg(@NotNull String name, int value) {
    return createJvmArg(name, String.valueOf(value));
  }

  @NotNull
  public static String createJvmArg(@NotNull String name, boolean value) {
    return createJvmArg(name, String.valueOf(value));
  }

  @NotNull
  public static String createJvmArg(@NotNull String name, @NotNull String value) {
    return String.format(JVM_ARG_FORMAT, name, value);
  }

  @NotNull
  public static String createProjectProperty(@NotNull String name, boolean value) {
    return createProjectProperty(name, String.valueOf(value));
  }

  @NotNull
  public static String createProjectProperty(@NotNull String name, int value) {
    return createProjectProperty(name, String.valueOf(value));
  }

  @NotNull
  public static String createProjectProperty(@NotNull String name, @NotNull String value) {
    return String.format(PROJECT_PROPERTY_FORMAT, name, value);
  }
}
