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

/**
 * Available project build modes used when building a project using the JPS framework.
 */
public enum BuildMode {
  /** Cleans the project.*/
  CLEAN,

  /** Compiles Java code and invokes Android build tools. */
  ASSEMBLE,

  /** Clean project and then {@link #ASSEMBLE}. */
  REBUILD,

  /** Compiles Java code, in selected modules, without invoking Android build tools. */
  COMPILE_JAVA,

  /** Generate Java source only (e.g. R.java). */
  SOURCE_GEN,

  /** Build with the Gradle "bundle" task*/
  BUNDLE,

  /** Build APKS from the Gradle "bundle" task*/
  APK_FROM_BUNDLE,

  /** Generate baseline profile */
  BASELINE_PROFILE_GEN,

  /** Generate baseline profile for all variants */
  BASELINE_PROFILE_GEN_ALL_VARIANTS;

  /**
   * This build mode is used when user invokes "Build" > "Make" or "Build" > "Rebuild". For these cases, Studio does not have a chance to
   * set the build mode in the project (unlike "Build" > "Compile") so when JPS is called there is no build mode specified.
   */
  public static final BuildMode DEFAULT_BUILD_MODE = COMPILE_JAVA;
}
