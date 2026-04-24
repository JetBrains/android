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
package com.android.tools.idea.gradle.dsl.android.api.dependencies

import org.jetbrains.annotations.NonNls

object AndroidCommonConfigurationNames {
  @NonNls val ANDROID_TEST_API: String = "androidTestApi"
  @JvmField @NonNls val ANDROID_TEST_COMPILE: String = "androidTestCompile"
  @JvmField @NonNls val ANDROID_TEST_IMPLEMENTATION: String = "androidTestImplementation"
  @NonNls val SCREENSHOT_TEST_API: String = "screenshotTestApi"
  @NonNls val SCREENSHOT_TEST_COMPILE: String = "screenshotTestCompile"
  @JvmField @NonNls val SCREENSHOT_TEST_IMPLEMENTATION: String = "screenshotTestImplementation"
}