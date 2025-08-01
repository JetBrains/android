
/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.util.AndroidStudioPreferences
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.project.ProjectManager

@Suppress("UnstableApiUsage")
class AndroidStudioSettingsInitializer : ApplicationInitializedListener {
  override suspend fun execute() {
    // Disable all settings sections that we don't want to be present in Android Studio.
    // See AndroidStudioPreferences for a full list.
    AndroidStudioPreferences.unregisterUnnecessaryExtensions(ProjectManager.getInstance().defaultProject)
  }
}
