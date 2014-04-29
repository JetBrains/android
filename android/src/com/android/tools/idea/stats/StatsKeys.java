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
package com.android.tools.idea.stats;

/**
 * String keys used with StatsTimeCollector.
 */
public class StatsKeys {

  /**
   * Indicates a project has been opened.
   * Used in AndroidGradleProjectComponent.projectOpened().
   * Type: string "gradle" or "not-gradle".
   */
  public static final String PROJECT_OPENED = "project-opened";

  /**
   * Measures Gradle sync time.
   * Starts in GradleProjectImporter.doImport().
   * Stops in PostProjectSyncTaskExecutor.onProjectSetupCompletion().
   * Type: time interval, in milliseconds.
   */
  public static final String GRADLE_SYNC_TIME = "sync-time";

  /**
   * Measures Gradle generate time.
   * Used in GradleTasksExecutor.run().
   * Type: time interval, in milliseconds.
   */
  public static final String GRADLE_GENERATE_TIME = "generate-time";

  /**
   * Measures Gradle assemble time.
   * Used in GradleTasksExecutor.run().
   * Type: time interval, in milliseconds.
   */
  public static final String GRADLE_ASSEMBLE_TIME = "assemble-time";

  /**
   * Measures Gradle compile time.
   * Used in GradleTasksExecutor.run().
   * Type: time interval, in milliseconds.
   */
  public static final String GRADLE_COMPILE_TIME = "compile-time";

  /**
   * Measures Gradle rebuild time (clean + compile).
   * Used in GradleTasksExecutor.run().
   * Type: time interval, in milliseconds.
   */
  public static final String GRADLE_REBUILD_TIME = "rebuild-time";

  /**
   * Measures Gradle clean time (clean + generate).
   * Used in GradleTasksExecutor.run().
   * Type: time interval, in milliseconds.
   */
  public static final String GRADLE_CLEAN_TIME = "clean-time";

  /**
   * Measures Gradle generic build time (when we failed to find a more specific key.)
   * Used in GradleTasksExecutor.run().
   * Type: time interval, in milliseconds.
   */
  public static final String GRADLE_BUILD_TIME = "build-time";

}
