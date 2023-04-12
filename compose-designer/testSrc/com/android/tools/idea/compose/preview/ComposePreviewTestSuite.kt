/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.testutils.JarTestSuiteRunner
import com.android.tools.tests.IdeaTestSuiteBase
import org.junit.runner.RunWith

@RunWith(JarTestSuiteRunner::class)
@JarTestSuiteRunner.ExcludeClasses(ComposePreviewTestSuite::class)
class ComposePreviewTestSuite : IdeaTestSuiteBase() {
  companion object {
    init {
      linkIntoOfflineMavenRepo("tools/adt/idea/compose-designer/test_deps.manifest")
      unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
      linkIntoOfflineMavenRepo(
        "tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest"
      )
      linkIntoOfflineMavenRepo("tools/base/third_party/kotlin/kotlin-m2repository.manifest")
    }
  }
}
