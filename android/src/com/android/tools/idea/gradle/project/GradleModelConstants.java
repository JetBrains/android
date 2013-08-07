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
package com.android.tools.idea.gradle.project;

import org.jetbrains.annotations.NonNls;

public final class GradleModelConstants {
  @NonNls public static final String ANDROID_GRADLE_MODEL_DEPENDENCY_NAME = "com.android.tools.build:gradle";

  // This error message is shown as a regular dialog.
  @NonNls static final String UNSUPPORTED_MODEL_VERSION_ERROR = String.format(
    "Project is using an old version of the Android Gradle plug-in. The minimum supported version is %1$s.\n\n" +
    "Please update the version of the dependency '%2$s' in your build.gradle files.",
    GradleModelVersionCheck.MINIMUM_SUPPORTED_VERSION.toString(), ANDROID_GRADLE_MODEL_DEPENDENCY_NAME);

  // This error message is used in the red bubble notification.
  @NonNls public static final String UNSUPPORTED_MODEL_VERSION_HTML_ERROR = String.format(
    "Project is using an old version of the Android Gradle plug-in. The minimum supported version is <b>%1$s</b>.<br><br>" +
    "Please update the version of the dependency '%2$s' in your build.gradle files.",
    GradleModelVersionCheck.MINIMUM_SUPPORTED_VERSION.toString(), ANDROID_GRADLE_MODEL_DEPENDENCY_NAME);

  GradleModelConstants() {
  }
}
