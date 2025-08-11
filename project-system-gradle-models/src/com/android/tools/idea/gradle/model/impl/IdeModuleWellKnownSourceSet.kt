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
package com.android.tools.idea.gradle.model.impl

/**
 * An Android or Java well-known source set in an IDE module group.
 *
 * Android source sets names are pre-defined and cannot be changed in Gradle configuration by users. In Java, Test Suites and KMP worlds source set
 * naming is more flexible. Note tha in case of source set name collision the original intent is assumed.
 */
enum class IdeModuleWellKnownSourceSet(
  override val sourceSetName: String,
  override val canBeConsumed: Boolean
) : IdeModuleSourceSet {
  /**
   * An Android source set or a special source set in Java/KMP, which is built by default Gradle tasks and on which other
   * project would depend on unless intentionally changed in the Gradle configuration.
   */
  MAIN("main", true),

  /**
   * A source set with text fixtures supported by the Android Gradle plugin and 'java-test-fixtures' plugin.
   */
  TEST_FIXTURES("testFixtures", true),

  UNIT_TEST("unitTest", false),
  ANDROID_TEST("androidTest", false),
  SCREENSHOT_TEST("screenshotTest", false);

  companion object {
    private val nameToValue = values().associateBy { it.sourceSetName }
    fun fromName(name: String): IdeModuleWellKnownSourceSet? = nameToValue[name]
  }
}