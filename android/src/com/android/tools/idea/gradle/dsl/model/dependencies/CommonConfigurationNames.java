/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import org.jetbrains.annotations.NonNls;

/**
 * Common configuration names used in dependencies.
 */
public final class CommonConfigurationNames {
  @NonNls public static final String ANDROID_TEST_COMPILE = "androidTestCompile";
  @NonNls public static final String APK = "apk";
  @NonNls public static final String CLASSPATH = "classpath";
  @NonNls public static final String COMPILE = "compile";
  @NonNls public static final String PROVIDED = "provided";
  @NonNls public static final String RUNTIME = "runtime";
  @NonNls public static final String TEST_COMPILE = "testCompile";

  private CommonConfigurationNames() {
  }
}
