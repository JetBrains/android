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
package com.android.tools.idea.gradle.messages;

/**
 * Common group names used to group Gradle-sync-related messages (by type/category) in the "Messages" tool window. These groups are also
 * displayed in the tool window.
 */
public final class CommonMessageGroupNames {
  public static final String PROJECT_STRUCTURE_ISSUES = "Project structure issues";
  public static final String MISSING_DEPENDENCIES_BETWEEN_MODULES = "Missing dependencies between modules";
  public static final String FAILED_TO_SET_UP_DEPENDENCIES = "Failed to set up dependencies";
  public static final String FAILED_TO_SET_UP_SDK = "Failed to set up SDK";
  public static final String UNRESOLVED_ANDROID_DEPENDENCIES = "Unresolved Android dependencies";
  public static final String UNRESOLVED_DEPENDENCIES = "Unresolved dependencies";
  public static final String VARIANT_SELECTION_CONFLICTS = "Variant selection conflicts";
  public static final String EXTRA_GENERATED_SOURCES = "Source folders generated at incorrect location";
  public static final String EXTERNAL_NATIVE_BUILD_ISSUES = "External Native Build Issues";
  public static final String UNHANDLED_SYNC_ISSUE_TYPE = "Gradle Sync Issue";

  private CommonMessageGroupNames() {
  }
}
