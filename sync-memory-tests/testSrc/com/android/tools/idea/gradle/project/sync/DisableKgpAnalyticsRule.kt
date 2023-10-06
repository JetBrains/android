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
package com.android.tools.idea.gradle.project.sync

import org.junit.rules.ExternalResource

class DisableKgpAnalyticsRule: ExternalResource() {
  override fun before() {
    mutateGradleProperties {
      properties.setProperty(GRADLE_PROPERTY_ENABLE_KOTLIN_PERFORMANCE_PROFILE, false.toString())
    }
  }

  companion object {
    const val GRADLE_PROPERTY_ENABLE_KOTLIN_PERFORMANCE_PROFILE = "enable_kotlin_performance_profile"
  }
}