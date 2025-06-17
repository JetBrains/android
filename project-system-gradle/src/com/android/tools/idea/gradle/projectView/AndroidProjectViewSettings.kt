/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.projectView

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationManager

interface AndroidProjectViewSettings {
  var defaultToProjectView: Boolean

  companion object {
    fun getInstance(): AndroidProjectViewSettings = ApplicationManager.getApplication().getService(AndroidProjectViewSettings::class.java)
    const val PROJECT_VIEW_KEY = "studio.projectview"
  }

  /*
   * Should the Project view used by default? The result depends on custom property
   *  [PROJECT_VIEW_KEY] and the application settings.
   */
  fun isProjectViewDefault(): Boolean {
    if (StudioFlags.SHOW_DEFAULT_PROJECT_VIEW_SETTINGS.get()) {
      if (java.lang.Boolean.getBoolean(PROJECT_VIEW_KEY))
        return true
      return defaultToProjectView
    } else {
      // If flag is not enabled, fall back to studio.projectview flag
      return java.lang.Boolean.getBoolean(PROJECT_VIEW_KEY)
    }
  }
}