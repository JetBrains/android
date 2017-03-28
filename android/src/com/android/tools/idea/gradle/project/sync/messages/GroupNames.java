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
package com.android.tools.idea.gradle.project.sync.messages;

/**
 * Common names used to group messages related to Gradle sync. These groups are displayed in the "Messages" tool window.
 */
public final class GroupNames {
  public static final String PROJECT_STRUCTURE_ISSUES = "Project Structure Issues";
  public static final String MISSING_DEPENDENCIES = "Missing Dependencies";
  public static final String SDK_SETUP_ISSUES = "SDK Setup Issues";
  public static final String VARIANT_SELECTION_CONFLICTS = "Variant Selection Conflicts";
  public static final String GENERATED_SOURCES = "Generated Sources";

  private GroupNames() {
  }
}
