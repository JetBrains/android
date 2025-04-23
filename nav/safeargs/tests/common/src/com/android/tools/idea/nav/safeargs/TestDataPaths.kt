/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs

import com.android.test.testutils.TestUtils.resolveWorkspacePath

/** Constants for safe args test project paths. */
object TestDataPaths {
  val TEST_DATA_ROOT: String =
    resolveWorkspacePath("tools/adt/idea/nav/safeargs/testData").toString()

  const val PROJECT_WITHOUT_SAFE_ARGS = "projects/projectWithoutSafeArgs"
  const val PROJECT_USING_JAVA_PLUGIN = "projects/safeArgsWithJavaPlugin"
  const val PROJECT_USING_KOTLIN_PLUGIN = "projects/safeArgsWithKotlinPlugin"
  const val MULTI_MODULE_PROJECT = "projects/safeArgsMultiModuleProject"
  const val SIMPLE_JAVA_PROJECT = "projects/SimpleJavaProject"
  const val SIMPLE_KOTLIN_PROJECT = "projects/SimpleKotlinProject"
}
