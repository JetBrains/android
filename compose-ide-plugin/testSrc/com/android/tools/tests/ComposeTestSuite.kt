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
package com.android.tools.tests

import com.android.testutils.JarTestSuiteRunner
import com.android.testutils.JarTestSuiteRunner.ExcludeClasses
import com.android.tools.tests.ComposeTestSuite
import org.junit.runner.RunWith

@RunWith(JarTestSuiteRunner::class)
@ExcludeClasses(ComposeTestSuite::class) // a suite mustn't contain itself
class ComposeTestSuite : IdeaTestSuiteBase() {
  companion object {
    init {
      leakChecker.enabled = false // TODO(b/264602053): fix leaks.
      unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
      linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest")
      linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest")
    }
  }
}