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
   * Type: string "gradle" or "not-gradle".
   */
  public static final String PROJECT_OPENED = "project-opened";

  public static final String GRADLE_SYNC_PROJECT_TIME_MS = "sync-time";

  public static final String GRADLE_GENERATE_SRC_TIME_MS = "generate-time";

  public static final String GRADLE_ASSEMBLE_TIME_MS = "assemble-time";

  public static final String GRADLE_COMPILE_TIME_MS = "compile-time";

  public static final String GRADLE_REBUILD_TIME_MS = "rebuild-time";

  public static final String GRADLE_CLEAN_TIME_MS = "clean-time";

  public static final String GRADLE_BUILD_TIME_MS = "build-time";
}
