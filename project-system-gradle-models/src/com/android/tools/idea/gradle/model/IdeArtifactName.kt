/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

enum class IdeArtifactName {
  MAIN, ANDROID_TEST, UNIT_TEST, TEST_FIXTURES, SCREENSHOT_TEST;

  companion object {
    @JvmStatic
    fun IdeArtifactName.toWellKnownSourceSet(): IdeModuleWellKnownSourceSet {
      return when (this) {
        MAIN -> IdeModuleWellKnownSourceSet.MAIN
        UNIT_TEST -> IdeModuleWellKnownSourceSet.UNIT_TEST
        ANDROID_TEST -> IdeModuleWellKnownSourceSet.ANDROID_TEST
        TEST_FIXTURES -> IdeModuleWellKnownSourceSet.TEST_FIXTURES
        SCREENSHOT_TEST -> IdeModuleWellKnownSourceSet.SCREENSHOT_TEST
      }
    }

    @JvmStatic
    fun IdeArtifactName.toPrintableName(): String {
      return when (this) {
        MAIN -> "Main"
        UNIT_TEST -> "UnitTest"
        ANDROID_TEST -> "AndroidTest"
        TEST_FIXTURES -> "TestFixtures"
        SCREENSHOT_TEST -> "ScreenshotTest"
      }
    }
  }
}
