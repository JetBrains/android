/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.tools.idea.gradle.util.AndroidStudioPreferences
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Project startup activity run ONLY in Android Studio. It should not be registered as part of the Android IntelliJ plugin.
 *
 * Note: project import is triggered by a different startup activity (maybe) run in parallel and as such anything that requires Android
 * models to be present should not be called here directly but instead by the appropriate listeners or callbacks.
 */
class AndroidStudioProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Disable all settings sections that we don't want to be present in Android Studio.
    // See AndroidStudioPreferences for a full list.
    AndroidStudioPreferences.cleanUpPreferences(project)
    // Custom notifications for Android Studio, un-wanted or un-needed when running as the Android IntelliJ plugin
    showNeededNotifications(project)
  }
}