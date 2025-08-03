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
package com.android.tools.idea.compose

const val TEST_DATA_PATH = "tools/adt/idea/compose-designer/testData"
const val SIMPLE_COMPOSE_PROJECT_PATH = "projects/SimpleComposeApplication"

/**
 * Relative paths to some useful files in the SimpleComposeApplication (
 * [SIMPLE_COMPOSE_PROJECT_PATH]) test project
 */
enum class SimpleComposeAppPaths(val path: String) {
  APP_MAIN_ACTIVITY("app/src/main/java/google/simpleapplication/MainActivity.kt"),
  APP_OTHER_PREVIEWS("app/src/main/java/google/simpleapplication/OtherPreviews.kt"),
  APP_PARAMETRIZED_PREVIEWS("app/src/main/java/google/simpleapplication/ParametrizedPreviews.kt"),
  APP_RENDER_ERROR("app/src/main/java/google/simpleapplication/RenderError.kt"),
  APP_PREVIEWS_ANDROID_TEST("app/src/androidTest/java/google/simpleapplication/AndroidPreviews.kt"),
  APP_PREVIEWS_UNIT_TEST("app/src/test/java/google/simpleapplication/UnitPreviews.kt"),
  APP_SIMPLE_APPLICATION_DIR("app/src/test/java/google/simpleapplication"),
  LIB_PREVIEWS("lib/src/main/java/google/simpleapplicationlib/Previews.kt"),
  LIB_PREVIEWS_ANDROID_TEST(
    "lib/src/androidTest/java/google/simpleapplicationlib/AndroidPreviews.kt"
  ),
  LIB_PREVIEWS_UNIT_TEST("lib/src/test/java/google/simpleapplicationlib/UnitPreviews.kt"),
  APP_BUILD_GRADLE("app/build.gradle"),
}
