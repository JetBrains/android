/*
 * Copyright (C) 2024 The Android Open Source Project
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

import org.jetbrains.plugins.gradle.settings.GradleSystemSettings

class AndroidStudioGradleSystemSettings : GradleSystemSettings() {
  /**
   * To avoid downloading project dependencies sources requires additional effort to analyze
   * potential implications that are going to be done as part of b/315455870
   */
  override fun isDownloadSources(): Boolean = true
}
